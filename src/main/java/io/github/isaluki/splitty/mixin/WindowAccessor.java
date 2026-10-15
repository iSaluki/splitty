package io.github.isaluki.splitty.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Window.class)
public interface WindowAccessor {
	@Accessor("windowedX")
	void splitty$setWindowedX(int x);

	@Accessor("windowedY")
	void splitty$setWindowedY(int y);
}
