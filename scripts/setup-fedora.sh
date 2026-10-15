#!/usr/bin/env bash
#
# Splitty setup for Fedora + Prism Launcher (Flatpak).
#
# What it does:
#   1. System: installs Flatpak, XWayland and controller udev rules if missing,
#      installs Prism Launcher from Flathub if missing, and makes sure Prism may use
#      X11 (needed to place the windows) and input devices (controllers).
#   2. Creates two Prism instances for Minecraft 26.3 + Fabric:
#        Splitty-P1  player 1, keyboard + mouse: Fabric API, Splitty
#        Splitty-P2  player 2, controller: Fabric API, Splitty, Controlify (+ its dependencies)
#      Mods come from Modrinth (hash-checked). Splitty comes from --splitty-jar, a
#      splitty-*.jar in the current folder, ~/Downloads or this repo's build/libs, or
#      the latest GitHub release (via the gh CLI if you're logged in).
#      Re-running updates the mods and settings; worlds and other files are kept.
#   3. Adds a "Splitty Split Screen" launcher (~/.local/bin/splitty-play and an app
#      menu entry) that starts both players, with player 2 on an offline account.
#
# Usage: scripts/setup-fedora.sh [options]
#   --splitty-jar PATH Use this Splitty jar
#   --p2-name NAME     Player 2's offline name (default: Player2)
#   --memory MB        Max memory per game (default: 4096)
#   --layout LAYOUT    top_bottom (default) or left_right
#   --skip-system      Don't install packages or change Flatpak permissions
#   -y, --yes          Don't ask for confirmation
#   -h, --help         Show this help

set -euo pipefail

APP_ID="org.prismlauncher.PrismLauncher"
MC_VERSION="26.3"
SPLITTY_REPO="iSaluki/splitty"
P1_ID="Splitty-P1"
P2_ID="Splitty-P2"

P2_NAME="Player2"
MEMORY_MB=4096
LAYOUT="top_bottom"
SKIP_SYSTEM=0
ASSUME_YES=0
SPLITTY_JAR=""

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
info() { printf '  %s\n' "$*"; }
warn() { printf '\033[33mwarning:\033[0m %s\n' "$*" >&2; }
die() { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }

usage() { sed -n '2,/^$/{s/^# \{0,1\}//;p}' "$0"; }

confirm() {
	[ "$ASSUME_YES" -eq 1 ] && return 0
	local reply
	read -r -p "$1 [Y/n] " reply
	[[ -z "$reply" || "$reply" =~ ^[Yy] ]]
}

while [ $# -gt 0 ]; do
	case "$1" in
		--splitty-jar) SPLITTY_JAR="${2:?--splitty-jar needs a value}"; shift 2 ;;
		--p2-name) P2_NAME="${2:?--p2-name needs a value}"; shift 2 ;;
		--memory) MEMORY_MB="${2:?--memory needs a value}"; shift 2 ;;
		--layout) LAYOUT="${2:?--layout needs a value}"; shift 2 ;;
		--skip-system) SKIP_SYSTEM=1; shift ;;
		-y | --yes) ASSUME_YES=1; shift ;;
		-h | --help) usage; exit 0 ;;
		*) die "unknown option: $1 (see --help)" ;;
	esac
done

[[ "$P2_NAME" =~ ^[A-Za-z0-9_]{3,16}$ ]] || die "--p2-name must be 3-16 letters, digits or underscores"
[[ "$MEMORY_MB" =~ ^[0-9]+$ && "$MEMORY_MB" -ge 1024 ]] || die "--memory must be a number of MB, at least 1024"
[[ "$LAYOUT" == "top_bottom" || "$LAYOUT" == "left_right" ]] || die "--layout must be top_bottom or left_right"
[ "$(id -u)" -ne 0 ] || die "run this as your normal user, not root (it uses sudo when needed)"

if ! grep -qs '^ID=fedora' /etc/os-release; then
	warn "this script is written for Fedora; continuing anyway"
fi
for cmd in curl python3; do
	command -v "$cmd" > /dev/null || die "$cmd is required"
done

# ---------------------------------------------------------------------------
# 1. System setup
# ---------------------------------------------------------------------------

prism_installed() { flatpak info "$APP_ID" > /dev/null 2>&1; }

if [ "$SKIP_SYSTEM" -eq 0 ]; then
	bold "System setup"

	# flatpak: Prism itself. Xwayland: lets Splitty position windows on a Wayland desktop.
	# game-devices-udev: lets SDL (used by Controlify) open controllers' raw HID devices.
	missing=()
	for pkg in flatpak xorg-x11-server-Xwayland game-devices-udev; do
		rpm -q "$pkg" > /dev/null 2>&1 || missing+=("$pkg")
	done
	if [ ${#missing[@]} -gt 0 ]; then
		info "Missing packages: ${missing[*]}"
		if confirm "Install them with sudo dnf?"; then
			sudo dnf install -y "${missing[@]}"
			if [[ " ${missing[*]} " == *" game-devices-udev "* ]]; then
				sudo udevadm control --reload-rules
				sudo udevadm trigger
				info "Controller rules installed. Re-plug your controller if it's connected."
			fi
		else
			warn "skipping package install; things may not work"
		fi
	else
		info "Packages: flatpak, Xwayland and controller udev rules are installed"
	fi

	command -v flatpak > /dev/null || die "flatpak is required"

	if ! prism_installed; then
		info "Prism Launcher isn't installed"
		if confirm "Install Prism Launcher from Flathub (for your user)?"; then
			flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
			flatpak install --user -y flathub "$APP_ID"
		else
			die "Prism Launcher (Flatpak) is required"
		fi
	else
		info "Prism Launcher: installed"
	fi

	# Prism's Flatpak normally has these already; make sure nobody removed them.
	flatpak override --user --socket=x11 --device=all "$APP_ID"
	info "Flatpak permissions: X11 and input devices allowed for Prism"
else
	command -v flatpak > /dev/null || die "flatpak is required"
	prism_installed || die "Prism Launcher (Flatpak) isn't installed"
fi

# Prism keeps instance data in memory and would overwrite our changes.
if flatpak ps --columns=application 2> /dev/null | grep -qx "$APP_ID"; then
	warn "Prism Launcher is running. Close it (and any games) before continuing."
	until ! flatpak ps --columns=application 2> /dev/null | grep -qx "$APP_ID"; do
		read -r -p "Press Enter once Prism is closed (Ctrl+C to cancel)... " _
	done
fi

# ---------------------------------------------------------------------------
# 2. Instances and mods
# ---------------------------------------------------------------------------

bold "Finding Splitty"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

newest_jar() {
	local dir newest=""
	for dir in "$@"; do
		for jar in "$dir"/splitty-*.jar; do
			[ -f "$jar" ] || continue
			[[ "$jar" == *-sources.jar ]] && continue
			if [ -z "$newest" ] || [ "$jar" -nt "$newest" ]; then newest="$jar"; fi
		done
	done
	printf '%s' "$newest"
}

if [ -n "$SPLITTY_JAR" ]; then
	[ -f "$SPLITTY_JAR" ] || die "no such file: $SPLITTY_JAR"
else
	SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
	SPLITTY_JAR="$(newest_jar "$PWD" "$HOME/Downloads" "$SCRIPT_DIR/../build/libs")"
fi
if [ -z "$SPLITTY_JAR" ] && command -v gh > /dev/null && gh auth status > /dev/null 2>&1; then
	if gh release download --repo "$SPLITTY_REPO" --pattern 'splitty-*.jar' --dir "$TMP_DIR" > /dev/null 2>&1; then
		SPLITTY_JAR="$(newest_jar "$TMP_DIR")"
	fi
fi
# Otherwise the Python step below tries the public GitHub release.
if [ -n "$SPLITTY_JAR" ]; then
	SPLITTY_JAR="$(cd "$(dirname "$SPLITTY_JAR")" && pwd)/$(basename "$SPLITTY_JAR")"
	info "Using $SPLITTY_JAR"
fi

bold "Creating Prism instances"

PRISM_DATA="$HOME/.var/app/$APP_ID/data/PrismLauncher"
mkdir -p "$PRISM_DATA"

export PRISM_DATA MC_VERSION SPLITTY_REPO P1_ID P2_ID MEMORY_MB LAYOUT SPLITTY_JAR
python3 -I - << 'PYTHON'
import hashlib, json, os, re, sys, urllib.error, urllib.parse, urllib.request

PRISM_DATA = os.environ["PRISM_DATA"]
MC = os.environ["MC_VERSION"]
MEMORY = int(os.environ["MEMORY_MB"])
LAYOUT = os.environ["LAYOUT"]
UA = {"User-Agent": "splitty-setup (github.com/%s)" % os.environ["SPLITTY_REPO"]}
MANAGED = ".splitty-managed"


def get_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
        return json.load(r)


def download(url, dest, sha512=None):
    if url.startswith("file://"):
        data = open(url[len("file://"):], "rb").read()
    else:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=300) as r:
            data = r.read()
    if sha512 and hashlib.sha512(data).hexdigest() != sha512:
        sys.exit("hash mismatch for %s" % url)
    with open(dest + ".part", "wb") as f:
        f.write(data)
    os.replace(dest + ".part", dest)


# --- INI files (Prism's instance.cfg / prismlauncher.cfg) -------------------

def ini_set(path, values):
    """Set keys in a Prism .cfg file, keeping everything else."""
    lines = open(path).read().splitlines() if os.path.exists(path) else ["[General]", "ConfigVersion=1.3"]
    has_general = any(l.strip() == "[General]" for l in lines)
    for key, value in values.items():
        if any(c in value for c in "=;,"):
            value = '"%s"' % value
        pattern = re.compile(r"^\s*%s\s*=" % re.escape(key))
        for i, line in enumerate(lines):
            if pattern.match(line):
                lines[i] = "%s=%s" % (key, value)
                break
        else:
            if has_general:
                idx = next(i for i, l in enumerate(lines) if l.strip() == "[General]")
                lines.insert(idx + 1, "%s=%s" % (key, value))
            else:
                lines.append("%s=%s" % (key, value))
    with open(path, "w") as f:
        f.write("\n".join(lines) + "\n")


def ini_get(path, key):
    if not os.path.exists(path):
        return None
    for line in open(path):
        m = re.match(r"^\s*%s\s*=(.*)$" % re.escape(key), line)
        if m:
            return m.group(1).strip().strip('"')
    return None


# --- Versions ---------------------------------------------------------------

loaders = get_json("https://meta.fabricmc.net/v2/versions/loader")
loader = next(l["version"] for l in loaders if l.get("stable"))
mc_meta = get_json("https://meta.prismlauncher.org/v1/net.minecraft/%s.json" % MC)
lwjgl = next((r.get("suggests") or r.get("equals") for r in mc_meta.get("requires", []) if r["uid"] == "org.lwjgl3"), None)
print("  Minecraft %s, Fabric Loader %s, LWJGL %s" % (MC, loader, lwjgl))


def modrinth_version(project, version_id=None):
    if version_id:
        return get_json("https://api.modrinth.com/v2/version/%s" % version_id)
    query = urllib.parse.urlencode({"game_versions": json.dumps([MC]), "loaders": json.dumps(["fabric"])})
    versions = get_json("https://api.modrinth.com/v2/project/%s/version?%s" % (project, query))
    if not versions:
        sys.exit("no %s build of Modrinth project '%s' for Minecraft %s yet" % ("fabric", project, MC))
    releases = [v for v in versions if v["version_type"] == "release"]
    return (releases or versions)[0]


def resolve(projects):
    """Modrinth projects plus their required dependencies -> list of files."""
    seen, files, queue = set(), [], [(p, None) for p in projects]
    while queue:
        project, version_id = queue.pop(0)
        version = modrinth_version(project, version_id)
        if version["project_id"] in seen:
            continue
        seen.add(version["project_id"])
        primary = next((f for f in version["files"] if f.get("primary")), version["files"][0])
        files.append({"name": primary["filename"], "url": primary["url"], "sha512": primary["hashes"]["sha512"]})
        print("    %s" % primary["filename"])
        for dep in version.get("dependencies", []):
            if dep["dependency_type"] == "required" and dep.get("project_id"):
                queue.append((dep["project_id"], dep.get("version_id")))
    return files


if os.environ.get("SPLITTY_JAR"):
    jar = os.environ["SPLITTY_JAR"]
    splitty = {"name": os.path.basename(jar), "url": "file://" + jar, "sha512": None}
    print("  Splitty: %s" % splitty["name"])
else:
    try:
        release = get_json("https://api.github.com/repos/%s/releases/latest" % os.environ["SPLITTY_REPO"])
    except urllib.error.HTTPError:
        sys.exit("Couldn't download Splitty from GitHub (the repository may be private).\n"
                 "Download splitty-x.y.z.jar from https://github.com/%s/releases yourself\n"
                 "and run this script again with --splitty-jar PATH (or put it in ~/Downloads)."
                 % os.environ["SPLITTY_REPO"])
    asset = next(a for a in release["assets"] if a["name"].endswith(".jar"))
    splitty = {"name": asset["name"], "url": asset["browser_download_url"], "sha512": None}
    print("  Splitty: %s (GitHub release %s)" % (asset["name"], release["tag_name"]))

print("  Player 1 mods:")
p1_mods = resolve(["fabric-api"]) + [splitty]
print("  Player 2 mods:")
# Resolved together so a dependency shared with Fabric API is only installed once.
p2_mods = resolve(["fabric-api", "controlify"]) + [splitty]

# --- Instances --------------------------------------------------------------

instance_dir = ini_get(os.path.join(PRISM_DATA, "prismlauncher.cfg"), "InstanceDir") or "instances"
if not os.path.isabs(instance_dir):
    instance_dir = os.path.join(PRISM_DATA, instance_dir)
os.makedirs(instance_dir, exist_ok=True)

components = [
    {"uid": "net.minecraft", "version": MC, "important": True},
    {"uid": "net.fabricmc.intermediary", "version": MC, "dependencyOnly": True},
    {"uid": "net.fabricmc.fabric-loader", "version": loader},
]
if lwjgl:
    components.insert(0, {"uid": "org.lwjgl3", "version": lwjgl, "dependencyOnly": True})


def setup_instance(inst_id, title, role, mods):
    root = os.path.join(instance_dir, inst_id)
    fresh = not os.path.exists(root)
    os.makedirs(root, exist_ok=True)
    game = os.path.join(root, ".minecraft")
    if not os.path.isdir(game):
        game = os.path.join(root, "minecraft")
    mods_dir = os.path.join(game, "mods")
    config_dir = os.path.join(game, "config")
    os.makedirs(mods_dir, exist_ok=True)
    os.makedirs(config_dir, exist_ok=True)

    # Swap the jars we installed last time for the current ones; leave other mods alone.
    managed = os.path.join(mods_dir, MANAGED)
    if os.path.exists(managed):
        for name in open(managed).read().split():
            path = os.path.join(mods_dir, os.path.basename(name))
            if os.path.isfile(path):
                os.remove(path)
    for mod in mods:
        download(mod["url"], os.path.join(mods_dir, mod["name"]), mod["sha512"])
    with open(managed, "w") as f:
        f.write("\n".join(m["name"] for m in mods) + "\n")

    with open(os.path.join(root, "mmc-pack.json"), "w") as f:
        json.dump({"formatVersion": 1, "components": components}, f, indent=4)

    ini_set(os.path.join(root, "instance.cfg"), {
        "InstanceType": "OneSix",
        "name": title,
        "OverrideJavaArgs": "true",
        "JvmArgs": "-Dsplitty.role=%s" % role,
        "OverrideMemory": "true",
        "MinMemAlloc": "1024",
        "MaxMemAlloc": str(MEMORY),
    })

    config_path = os.path.join(config_dir, "splitty.json")
    config = {}
    if os.path.exists(config_path):
        try:
            config = json.load(open(config_path))
        except ValueError:
            config = {}
    config.update({"role": role, "layout": LAYOUT})
    with open(config_path, "w") as f:
        json.dump(config, f, indent=2)

    print("  %s %s -> %s" % ("Created" if fresh else "Updated", title, root))


setup_instance(os.environ["P1_ID"], "Splitty P1 (keyboard)", "host", p1_mods)
setup_instance(os.environ["P2_ID"], "Splitty P2 (controller)", "guest", p2_mods)

# Let Prism download the Java version Minecraft 26.3 needs (Java 25) by itself.
ini_set(os.path.join(PRISM_DATA, "prismlauncher.cfg"), {
    "AutomaticJavaSwitch": "true",
    "AutomaticJavaDownload": "true",
})
PYTHON

# ---------------------------------------------------------------------------
# 3. Launcher
# ---------------------------------------------------------------------------

bold "Installing the Splitty launcher"

BIN_DIR="$HOME/.local/bin"
LAUNCHER="$BIN_DIR/splitty-play"
mkdir -p "$BIN_DIR" "$HOME/.local/share/applications"

cat > "$LAUNCHER" << EOF
#!/usr/bin/env bash
# Starts both Splitty players. Generated by Splitty's setup-fedora.sh.
set -euo pipefail
APP_ID="$APP_ID"

# Player 1 uses Prism's default (Microsoft) account. Starting it also starts Prism.
flatpak run "\$APP_ID" --launch "$P1_ID" &

# Wait for Prism to be ready to take a second command.
for _ in \$(seq 1 30); do
	flatpak ps --columns=application 2> /dev/null | grep -qx "\$APP_ID" && break
	sleep 1
done
sleep 5

# Player 2 joins on an offline account with a different name.
flatpak run "\$APP_ID" --launch "$P2_ID" --offline "$P2_NAME"
wait
EOF
chmod +x "$LAUNCHER"

cat > "$HOME/.local/share/applications/splitty.desktop" << EOF
[Desktop Entry]
Type=Application
Name=Splitty Split Screen
Comment=Minecraft split screen: keyboard + controller
Exec=$LAUNCHER
Icon=$APP_ID
Categories=Game;
Terminal=false
EOF
command -v update-desktop-database > /dev/null && update-desktop-database "$HOME/.local/share/applications" > /dev/null 2>&1 || true
info "Command: $LAUNCHER"
info "App menu: Splitty Split Screen"

echo
bold "Done!"
cat << EOF
Before the first game:
  1. Open Prism Launcher and sign in with your Microsoft account
     (Accounts -> Manage Accounts -> Add Microsoft). Player 1 plays on it, and
     Prism needs it to allow Player 2's offline account.
  2. Plug in the controller.

Then start "Splitty Split Screen" from the app menu (or run splitty-play).
In the top window (Player 1), open or create a single-player world. Player 2
($P2_NAME) joins automatically in the bottom window.

The first launch downloads Minecraft and Java 25, so it takes a few minutes.
EOF
