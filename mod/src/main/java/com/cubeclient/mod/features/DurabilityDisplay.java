package com.cubeclient.mod.features;

import com.cubeclient.mod.gui.HudPosition;
import com.cubeclient.mod.gui.HudRenderUtil;
import com.cubeclient.mod.gui.Theme;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.PositionedHudFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.List;

public class DurabilityDisplay implements PositionedHudFeature {
    @Override
    public String id() {
        return "durability";
    }

    @Override
    public String displayName() {
        return "내구도 표시";
    }

    @Override
    public Category category() {
        return Category.HUD;
    }

    @Override
    public HudPosition defaultPosition() {
        return HudPosition.of(0.80, 0.55, 1.0);
    }

    @Override
    public void render(DrawContext context, HudPosition pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        // 배열 인덱스(PlayerInventory.getArmorStack(int))를 추측해서 쓰지 않는다 — 이름 기반인
        // getEquippedStack(EquipmentSlot)을 써서 순서를 오해할 여지 자체를 없앤다.
        List<ItemStack> slots = List.of(
            client.player.getEquippedStack(EquipmentSlot.HEAD),
            client.player.getEquippedStack(EquipmentSlot.CHEST),
            client.player.getEquippedStack(EquipmentSlot.LEGS),
            client.player.getEquippedStack(EquipmentSlot.FEET),
            client.player.getEquippedStack(EquipmentSlot.MAINHAND)
        );
        HudRenderUtil.drawScaled(context, pos, (ctx, x, y) -> {
            int row = 0;
            for (ItemStack stack : slots) {
                if (stack.isEmpty() || !stack.isDamageable()) {
                    continue;
                }
                int rowY = y + row * 20;
                ctx.drawItem(stack, x, rowY);
                int remaining = stack.getMaxDamage() - stack.getDamage();
                ctx.drawTextWithShadow(client.textRenderer, String.valueOf(remaining), x + 20, rowY + 4, Theme.TEXT);
                row++;
            }
        });
    }
}
