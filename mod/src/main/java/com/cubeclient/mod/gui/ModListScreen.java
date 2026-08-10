package com.cubeclient.mod.gui;

import com.cubeclient.mod.config.CachedConfig;
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
    private static final int MARGIN = 12;
    private static final int TAB_HEIGHT = 20;
    /** Below the title, the tab row, and the search row. */
    private static final int GRID_TOP = 80;
    /** Space reserved at the bottom for the "죽은 위치 전체 삭제" button (height + its own margin
     * + a gap above it) — computeMaxScroll must stop the grid short of this or a scrolled-down
     * card row draws under/behind the button, since cards have no bottom scissor (only a top one,
     * see FeatureCard). */
    private static final int BOTTOM_BUTTON_AREA = 36;

    private final Screen parent;
    private final FeatureRegistry registry;
    private final CachedConfig cachedConfig;
    private final com.cubeclient.mod.death.DeathLocationStore deathLocationStore;

    private ModConfig config;
    private Category activeCategory; // null = 전부
    private String searchText = "";

    private TextFieldWidget searchField;
    private final List<FeatureCard> cards = new ArrayList<>();

    /**
     * Set when a rebuild is needed, acted on at the start of the next frame.
     *
     * <p>Favoriting re-sorts the grid, and a card's own click handler is what triggers it — so
     * rebuilding there would tear down and replace the widget list while Minecraft is still
     * dispatching the mouse event through the very card that asked for it. Deferring one frame
     * keeps the mutation outside that dispatch entirely.
     */
    private boolean rebuildQueued;

    private int scrollOffset;
    private static final int SCROLL_STEP = 20;

    public ModListScreen(Screen parent, FeatureRegistry registry, CachedConfig cachedConfig,
                          com.cubeclient.mod.death.DeathLocationStore deathLocationStore) {
        super(Text.literal("클라이언트 설정"));
        this.parent = parent;
        this.registry = registry;
        this.cachedConfig = cachedConfig;
        this.deathLocationStore = deathLocationStore;
    }

    @Override
    protected void init() {
        this.config = loadConfigOrEmpty();

        // Tabs get a row to themselves and the search box gets the next one. Sharing one row put
        // the last tab straight on top of the search field: Minecraft never scales a GUI below
        // 320 units wide, and five readable tabs plus a usable text box do not fit in 320.
        int tabY = 24;
        int tabCount = Category.values().length + 2; // the categories, plus 전부, plus HUD 조절
        int tabGap = 4;
        int tabWidth = Math.min(70, (width - 2 * MARGIN - tabGap * (tabCount - 1)) / tabCount);
        int tabX = MARGIN;

        // 전부 leads: it is the state the screen opens in, so it reads as the leftmost tab.
        addDrawableChild(ButtonWidget.builder(Text.literal("전부"), b -> {
            this.activeCategory = null;
            rebuildQueued = true;
        }).dimensions(tabX, tabY, tabWidth, TAB_HEIGHT).build());
        tabX += tabWidth + tabGap;

        for (Category category : Category.values()) {
            Category thisCategory = category;
            // Queued rather than immediate: every one of these fires from inside Minecraft's
            // own input dispatch, and rebuilding replaces the widget list being dispatched over.
            addDrawableChild(ButtonWidget.builder(Text.literal(category.displayName()), b -> {
                this.activeCategory = thisCategory;
                rebuildQueued = true;
            }).dimensions(tabX, tabY, tabWidth, TAB_HEIGHT).build());
            tabX += tabWidth + tabGap;
        }

        // Category enum에 넣지 않는다 — 필터가 아니라 다른 화면으로의 이동이라 의미가 다르다.
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD 조절"), b ->
            client.setScreen(new HudEditorScreen(this, registry, cachedConfig))
        ).dimensions(tabX, tabY, tabWidth, TAB_HEIGHT).build());
        tabX += tabWidth + tabGap;

        searchField = new TextFieldWidget(
            textRenderer, MARGIN, tabY + TAB_HEIGHT + 4, width - 2 * MARGIN, 20,
            Text.literal("모드 검색"));
        // TextFieldWidget's message is narration only; this is what actually shows in the empty
        // box, which otherwise gives no hint that it can be typed into.
        searchField.setPlaceholder(Text.literal("모드 검색"));
        searchField.setChangedListener(text -> {
            this.searchText = text;
            rebuildQueued = true;
        });
        addDrawableChild(searchField);

        addDrawableChild(ButtonWidget.builder(Text.literal("죽은 위치 전체 삭제"), b -> clearDeathLocations())
            .dimensions(width - 160 - MARGIN, height - 20 - 8, 160, 20)
            .build());

        rebuildCards();
    }

    private ModConfig loadConfigOrEmpty() {
        return cachedConfig.current();
    }

    private void rebuildCards() {
        cards.forEach(this::remove);
        cards.clear();

        // A category/search change can shrink the visible set while still scrolled deep into a
        // longer list; without re-clamping here, startY below is computed from a stale, too-large
        // scrollOffset and every card lands above GRID_TOP, inside the scissor region render()
        // clips out — the grid looks empty even though matching cards exist.
        scrollOffset = Math.min(scrollOffset, computeMaxScroll());

        List<Feature> visible = registry.list(activeCategory, searchText, config.favorites());

        int cardWidth = 140;
        int cardHeight = 90;
        int gap = 12;
        // Four columns only when four fit. A fixed count sent cards off the right edge on a
        // narrow window, and Minecraft scales GUIs down to 320 units wide.
        int columns = Math.max(1, (width - 2 * MARGIN + gap) / (cardWidth + gap));
        int startX = MARGIN;
        int startY = GRID_TOP - scrollOffset;

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
                this::onFavoriteToggle,
                GRID_TOP
            );
            cards.add(card);
            addDrawableChild(card);
        }
    }

    private void onToggle(Feature feature) {
        Map<String, Boolean> enabled = new HashMap<>(config.enabled());
        enabled.put(feature.id(), !config.isEnabled(feature.id()));
        config = new ModConfig(enabled, config.favorites(), config.positions());
        persist();
    }

    private void onFavoriteToggle(Feature feature) {
        Set<String> favorites = new HashSet<>(config.favorites());
        if (!favorites.remove(feature.id())) {
            favorites.add(feature.id());
        }
        config = new ModConfig(config.enabled(), favorites, config.positions());
        persist();
        // Favorite order changed, so the grid must re-sort rather than just repaint — but not
        // from inside this click. See rebuildQueued.
        rebuildQueued = true;
    }

    private void persist() {
        try {
            cachedConfig.save(config);
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("설정을 저장하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    private void clearDeathLocations() {
        try {
            deathLocationStore.clearAll();
        } catch (IOException e) {
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    Text.literal("죽은 위치를 삭제하지 못했습니다: " + e.getMessage()), false);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (rebuildQueued) {
            rebuildQueued = false;
            rebuildCards();
        }

        context.fill(0, 0, width, height, Theme.GROUND);
        // No screen-wide scissor here — it used to wrap this whole call, which clipped away every
        // widget super.render() draws, including the tab row and search field above GRID_TOP (both
        // end up invisible, though still clickable, since scissor doesn't affect hit-testing).
        // Cards scrolled fully above the grid now hide themselves (see FeatureCard.renderWidget /
        // isMouseOver), and a card straddling the visibleTop boundary scopes its own scissor
        // internally to clip just the portion above it — so no screen-wide scissor is needed here.
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, Theme.TEXT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = computeMaxScroll();
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * SCROLL_STEP));
        rebuildQueued = true;
        return true;
    }

    private int computeMaxScroll() {
        List<Feature> visible = registry.list(activeCategory, searchText, config.favorites());
        int cardWidth = 140;
        int gap = 12;
        int columns = Math.max(1, (width - 2 * MARGIN + gap) / (cardWidth + gap));
        int rows = (visible.size() + columns - 1) / columns;
        int cardHeight = 90;
        int totalContentHeight = rows * (cardHeight + gap);
        int visibleHeight = height - GRID_TOP - BOTTOM_BUTTON_AREA;
        return Math.max(0, totalContentHeight - visibleHeight);
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }
}
