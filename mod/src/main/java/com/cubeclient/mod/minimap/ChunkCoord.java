package com.cubeclient.mod.minimap;

/** 순수 청크 좌표 값 타입. net.minecraft.util.math.ChunkPos는 static 초기화 시 게임
 * 레지스트리를 참조해서 부팅되지 않은 JUnit 환경에서 인스턴스화하면 죽는다(javap로 확인) —
 * 이 모드의 순수/테스트 계층은 전부 이 타입을 쓰고, 실제 게임 안에서 World를 만질 때만
 * ChunkPos로 변환한다. */
public record ChunkCoord(int x, int z) {}
