package com.cubeclient.mod.zoom;

/**
 * ZoomKey(일반 코드)와 GameRendererMixin(믹신) 사이의 유일한 다리. C키로 줌 중일 때만
 * 유효한 FOV 값을 담고, 아니면 음수(무효)로 둔다. static인 이유: 믹신은 별도 클래스라
 * ZoomKey 인스턴스를 직접 참조할 수 없고, 이 모드에 ZoomKey 인스턴스는 항상 하나뿐이라
 * 싱글턴을 따로 만들 필요가 없다.
 */
public final class ZoomFovState {
    private static volatile float overrideFov = -1f;

    private ZoomFovState() {}

    public static void set(float fov) {
        overrideFov = fov;
    }

    public static void clear() {
        overrideFov = -1f;
    }

    public static float get() {
        return overrideFov;
    }
}
