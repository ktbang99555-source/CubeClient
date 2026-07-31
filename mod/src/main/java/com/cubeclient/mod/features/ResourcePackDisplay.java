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

    // 바닐라 기본 팩("vanilla")은 항상 켜져 있어 목록에 포함되지만, 사용자 입장에서는 "리소스팩을
    // 적용 중"이라 여기지 않으므로 표시에서 제외한다.
    private static List<String> enabledDisplayNames(Collection<ResourcePackProfile> enabled) {
        return enabled.stream()
            .filter(profile -> !"vanilla".equals(profile.getId()))
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
