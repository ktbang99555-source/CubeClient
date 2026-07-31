package com.cubeclient.mod.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 이 프로젝트의 유일한 믹신. ZoomKey의 진짜 8배 줌은 바닐라 FOV 옵션(30~110로 클램프됨,
 * javap로 확인)을 우회해야만 가능하다 — 실제 렌더링에 쓰이는 FOV를 계산하는 지점이 이
 * private 메서드뿐이고, 공개 API로는 대체할 방법이 없다(Fabric API 전체에 FOV 관련 훅이
 * 없음, 확인됨). 반환값 하나만 가로챈다 — 그 외 아무 것도 건드리지 않는다. ZoomKey가 줌
 * 중이 아니면 {@link ZoomFovState#get()}이 음수를 돌려주므로 이 훅은 사실상 아무 일도
 * 하지 않는다.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void cubeclient$overrideZoomFov(Camera camera, float tickDelta, boolean changingFov,
            CallbackInfoReturnable<Float> cir) {
        float override = ZoomFovState.get();
        if (override > 0f) {
            cir.setReturnValue(override);
        }
    }
}
