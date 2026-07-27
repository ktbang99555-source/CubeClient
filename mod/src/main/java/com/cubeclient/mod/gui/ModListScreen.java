package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.ConfigStore;
import com.cubeclient.mod.config.ModConfig;
import com.cubeclient.mod.registry.Category;
import com.cubeclient.mod.registry.Feature;
import com.cubeclient.mod.registry.FeatureRegistry;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The "클라이언트 설정" screen. Loads the saved config once on open, mutates an in-memory copy
 * as the user toggles/favorites cards, and writes it back on every change rather than only on
 * close — a crash mid-session (or the player just hitting Escape without a dedicated Save
 * button, which this screen doesn't have) must not lose a toggle the player already made.
 */
public class ModListScreen extends Screen {
    private final Screen parent;
    private final FeatureRegistry registry;
    private final ConfigStore configStore;

    private ModConfig config;
    private Category activeCategory; // null = 전부
    private String searchText = "";

    private TextFieldWidget searchField;
    private final List<FeatureCard> cards = new ArrayList<>();

    public ModListScreen(Screen parent, FeatureRegistry registry, ConfigStore configStore) {
        super(Text.literal("클라이언트 설정"));
        this.parent = parent;
        this.registry = registry;
        this.configStore = configStore;
    }

    @Override
    protected void init() {
        this.config = loadConfigOrEmpty();

        int tabY = 24;
        int tabX = 12;
        for (Category category : Category.values()) {
            Category thisCategory = category;
            addDrawableChild(ButtonWidget.builder(Text.literal(category.displayName()), b -> {
                this.activeCategory = thisCategory;
                rebuildCards();
            }).dimensions(tabX, tabY, 70, 20).build());
            tabX += 74;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("전부"), b -> {
            this.activeCategory = null;
            rebuildCards();
        }).dimensions(tabX, tabY, 70, 20).build());

        searchField = new TextFieldWidget(textRenderer, width - 160, tabY, 148, 20, Text.literal("모드 검색"));
        searchField.setChangedListener(text -> {
            this.searchText = text;
            rebuildCards();
        });
        addDrawableChild(searchField);

        rebuildCards();
    }

    private ModConfig loadConfigOrEmpty() {
        try {
            return configStore.load();
        } catch (IOException e) {
            // A screen cannot surface a launcher-style error event; a broken config is treated
            // as an empty one and the player simply starts from every feature off.
            return ModConfig.empty();
        }
    }

    private void rebuildCards() {
        cards.forEach(this::remove);
        cards.clear();

        List<Feature> visible = registry.list(activeCategory, searchText, config.favorites());

        int columns = 4;
        int cardWidth = 140;
        int cardHeight = 90;
        int gap = 12;
        int startX = 12;
        int startY = 56;

        for (int i = 0; i < visible.size(); i++) {
            Feature feature = visible.get(i);
            int col = i % columns;
            int row = i / columns;
            FeatureCard card = new FeatureCard(
                startX + col * (cardWidth + gap),
                startY + row * (cardHeight + gap),
                cardWidth, cardHeight,
                feature,
                config.isEnabled(feature.id()),
                config.favorites().contains(feature.id()),
                this::onToggle,
                this::onFavoriteToggle
            );
            cards.add(card);
            addDrawableChild(card);
        }
    }

    private void onToggle(Feature feature) {
        Map<String, Boolean> enabled = new HashMap<>(config.enabled());
        enabled.put(feature.id(), !config.isEnabled(feature.id()));
        config = new ModConfig(enabled, config.favorites());
        persist();
    }

    private void onFavoriteToggle(Feature feature) {
        Set<String> favorites = new HashSet<>(config.favorites());
        if (!favorites.remove(feature.id())) {
            favorites.add(feature.id());
        }
        config = new ModConfig(config.enabled(), favorites);
        persist();
        // Favorite order changed, so the grid must re-sort, not just repaint.
        rebuildCards();
    }

    private void persist() {
        try {
            configStore.save(config);
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("설정을 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, Theme.GROUND);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, Theme.TEXT);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
