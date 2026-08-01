// mod/src/main/java/com/cubeclient/mod/minimap/ArrowShape.java
package com.cubeclient.mod.minimap;

/** 미니맵 중심에 그리는 플레이어 화살표의 순수 도형 판정. yaw 부호 관례는 바닐라
 * Entity.getRotationVector()와 동일: dirX = -sin(yaw), dirZ = cos(yaw) (yaw=0 -> 남쪽/+Z,
 * yaw=180 -> 북쪽/-Z). px, pz는 화살표 중심 기준 픽셀 오프셋. */
public final class ArrowShape {
    private ArrowShape() {}

    private static final double TIP_LENGTH = 6.0;
    private static final double BASE_LENGTH = 5.0;
    private static final double BASE_HALF_WIDTH = 4.0;

    public static boolean isInsideArrow(double px, double pz, double yawDegrees) {
        double yawRad = Math.toRadians(yawDegrees);
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);
        double perpX = -dirZ;
        double perpZ = dirX;

        double tipX = dirX * TIP_LENGTH;
        double tipZ = dirZ * TIP_LENGTH;
        double baseCenterX = -dirX * BASE_LENGTH;
        double baseCenterZ = -dirZ * BASE_LENGTH;
        double baseLeftX = baseCenterX + perpX * BASE_HALF_WIDTH;
        double baseLeftZ = baseCenterZ + perpZ * BASE_HALF_WIDTH;
        double baseRightX = baseCenterX - perpX * BASE_HALF_WIDTH;
        double baseRightZ = baseCenterZ - perpZ * BASE_HALF_WIDTH;

        return isInsideTriangle(px, pz, tipX, tipZ, baseLeftX, baseLeftZ, baseRightX, baseRightZ);
    }

    private static boolean isInsideTriangle(double px, double pz, double ax, double az,
                                             double bx, double bz, double cx, double cz) {
        double d1 = cross(px - ax, pz - az, bx - ax, bz - az);
        double d2 = cross(px - bx, pz - bz, cx - bx, cz - bz);
        double d3 = cross(px - cx, pz - cz, ax - cx, az - cz);
        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);
        return !(hasNeg && hasPos);
    }

    private static double cross(double ax, double az, double bx, double bz) {
        return ax * bz - az * bx;
    }
}
