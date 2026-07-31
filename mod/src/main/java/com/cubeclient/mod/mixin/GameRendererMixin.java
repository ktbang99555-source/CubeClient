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
    // 시그니처까지 못박아 둔다 — 이름만 쓰면 훗날 마인크래프트가 getFov 오버로드를 추가했을 때
    // 엉뚱한 쪽에 조용히 붙어버릴 수 있다(defaultRequire=1은 그래도 만족되므로 눈치채기 어렵다).
    @Inject(method = "getFov(Lnet/minecraft/client/render/Camera;FZ)F", at = @At("RETURN"),
            cancellable = true)
    private void cubeclient$overrideZoomFov(Camera camera, float tickDelta, boolean changingFov,
            CallbackInfoReturnable<Float> cir) {
        float override = ZoomFovState.get();
        // changingFov 검사가 핵심이다. GameRenderer 안에서 이 메서드를 부르는 곳은 딱 둘 —
        // renderWorld(changingFov=true, 플레이어 시야)와 renderHand(changingFov=false, 들고 있는
        // 아이템). 바닐라가 손에 든 아이템을 FOV 효과에서 일부러 빼놓는 게 후자다. 이걸 안 보고
        // 덮어쓰면 8배 줌에서 손에 든 아이템 투영까지 ~9배로 부풀어 화면 밖으로 넘친다.
        if (override > 0f && changingFov) {
            cir.setReturnValue(override);
        }
    }
}
