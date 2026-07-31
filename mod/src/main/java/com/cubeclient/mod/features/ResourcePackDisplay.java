package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.ResourcePackProfile;

import java.util.Collection;
import java.util.List;

public class ResourcePackDisplay implements PositionedHudFeature {
    @Override
    public String id() {
        return "resource_pack";
    }

    @Override
    public String displayName() {
        return "리소스팩 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.01, 0.21, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        String text = formatLine(enabledDisplayNames(client.getResourcePackManager().getEnabledProfiles()));
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) ->
            ctx.drawTextWithShadow(client.textRenderer, text, x, y, Theme.TEXT));
    }

    // 바닐라 기본 팩과 Fabric이 모드마다 자동 등록하는 합성 리소스팩(예: "Fabric Mod
    // \"Fabric API\"")은 전부 pinned(사용자가 껐다 켰다 할 수 없는, 항상 켜진 팩)로 잡힌다.
    // id만으로 "vanilla"를 걸러내던 예전 필터는 이 자동 등록 팩들을 놓쳐서, 리소스팩을 하나도
    // 안 넣은 상태에서도 로드된 Fabric 모드 전부가 "리소스팩"처럼 나열되는 버그가 있었다
    // (실기기 테스트로 발견). isPinned()로 걸러내면 실제 사용자가 resourcepacks 폴더에 넣고
    // 켠 팩만 남는다.
    private static List<String> enabledDisplayNames(Collection<ResourcePackProfile> enabled) {
        return enabled.stream()
            .filter(profile -> !profile.isPinned() && !"vanilla".equals(profile.getId()))
            .map(profile -> profile.getDisplayName().getString())
            .toList();
    }

    /** 바닐라 제외까지 끝난 이름 목록을 받는 순수 포맷팅 — Minecraft 클래스 없이 테스트한다. */
    public static String formatLine(List<String> displayNames) {
        if (displayNames.isEmpty()) {
            return "리소스팩 없음";
        }
        return String.join(", ", displayNames);
    }
}
