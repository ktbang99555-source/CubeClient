package com.cubeclient.mod.death;

/** 죽은 위치 기록 하나. dimensionId는 RegistryKey<World>가 아니라 문자열(예:
 * "minecraft:overworld")로 저장한다 — JSON 저장이 단순해지고, B4의 ChunkPos가 겪은 것과 같은
 * "static 초기화가 게임 부팅을 요구하는 클래스를 순수 계층에 끌어들이는" 위험도 원천적으로 없다. */
public record DeathLocation(String worldId, String dimensionId, double x, double y, double z) {}
