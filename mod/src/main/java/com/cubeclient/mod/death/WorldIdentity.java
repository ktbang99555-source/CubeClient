package com.cubeclient.mod.death;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.world.World;

/** 죽은 위치를 어느 월드/서버에서 기록했는지 구분하는 문자열 키. 싱글플레이는 세이브 이름,
 * 멀티플레이는 서버 주소를 쓴다 — 같은 차원 종류(예: 오버월드)라도 실제로는 완전히 다른 물리적
 * 장소인 다른 월드/서버의 좌표와 섞이지 않게 하기 위함(B4의 MinimapChunkCache가 겪은
 * "같은 차원 키, 다른 World 인스턴스" 문제와 같은 종류의 함정). */
public final class WorldIdentity {
    private WorldIdentity() {}

    public static String currentWorldId(MinecraftClient client) {
        if (client.isInSingleplayer()) {
            // isInSingleplayer()는 server 필드를 null 체크 없이 그대로 반환하는 raw 필드 체크다.
            // MinecraftClient.disconnect(...)가 싱글플레이 종료 시 server를 먼저 null로 만들고,
            // 그 다음 "Saving world" 대기 동안 여러 프레임에 걸쳐 render(false)를 계속 호출한다 —
            // 이 구간에서 world/player는 non-null, isInSingleplayer()는 여전히 true, getServer()는
            // null이라 여기서 null 체크 없이 바로 쓰면 월드 종료할 때마다 NPE가 난다.
            IntegratedServer server = client.getServer();
            if (server != null) {
                return "singleplayer:" + server.getSaveProperties().getLevelName();
            }
        }
        ServerInfo serverEntry = client.getCurrentServerEntry();
        return serverEntry != null ? "server:" + serverEntry.address : "unknown";
    }

    public static String currentDimensionId(World world) {
        return world.getRegistryKey().getValue().toString();
    }
}
