package com.cubeclient.mod.death;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.world.World;

/** 죽은 위치를 어느 월드/서버에서 기록했는지 구분하는 문자열 키. 싱글플레이는 세이브 이름,
 * 멀티플레이는 서버 주소를 쓴다 — 같은 차원 종류(예: 오버월드)라도 실제로는 완전히 다른 물리적
 * 장소인 다른 월드/서버의 좌표와 섞이지 않게 하기 위함(B4의 MinimapChunkCache가 겪은
 * "같은 차원 키, 다른 World 인스턴스" 문제와 같은 종류의 함정). */
public final class WorldIdentity {
    private WorldIdentity() {}

    public static String currentWorldId(MinecraftClient client) {
        if (client.isInSingleplayer()) {
            return "singleplayer:" + client.getServer().getSaveProperties().getLevelName();
        }
        ServerInfo serverEntry = client.getCurrentServerEntry();
        return serverEntry != null ? "server:" + serverEntry.address : "unknown";
    }

    public static String currentDimensionId(World world) {
        return world.getRegistryKey().getValue().toString();
    }
}
