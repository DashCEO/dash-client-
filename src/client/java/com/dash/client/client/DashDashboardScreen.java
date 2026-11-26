package com.dash.client.client;

import static com.dash.client.client.DashLanguage.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public final class DashDashboardScreen extends Screen {
    private static final int TEXT = 0xFFF3F5F8;
    private static final int MUTED = 0xFF9DA7B5;
    private static final int EDGE = 0xFF303843;
    private static final int ROW = 0xFF1A2028;
    private static final int GAP = 7;
    private static final int ROW_HEIGHT = 23;
    private static final int[] SWATCHES = {0xFF6CDFF4, 0xFF7C9BFF, 0xFFB990FF, 0xFFFF7E89, 0xFFF3B86B};
    private static final DashHudLayout.Widget[] ELEMENTS = DashHudLayout.Widget.values();
    private static final TriggerBot.TargetType[] TARGETS = TriggerBot.TargetType.values();
    private static final DashLanguage[] LANGUAGES = DashLanguage.values();
    private static final Nav[] NAV = Nav.values();
    private static final String[] COMPACT_TITLES = {"Function", "Preview", "Elements"};

    private final Screen parent;
    private final long opened = System.nanoTime();
    private final List<ModuleRow> modules = new ArrayList<>();
    private final List<ModuleRow> results = new ArrayList<>();
    private final Map<String, KeyMapping> bindings = new HashMap<>();
    private final Hit[] hits = new Hit[160];
    private final float[] elementToggles = new float[ELEMENTS.length];
    private EditBox search;
    private Font uiFont;
    private Nav category = Nav.ALL;
    private ModuleRow selected;
    private boolean favoritesOnly;
    private boolean compact;
    private int compactSection;
    private int moduleOffset, hudOffset;
    private int hitCount;
    private int lastMouseX, lastMouseY;
    private int panelWidth, panelHeight, railWidth, contentX, contentWidth, bodyY, bodyHeight;
    private int moduleX, moduleWidth, previewX, previewWidth, elementsX, elementsWidth;
    private int moduleListY, moduleListHeight, elementsListY, elementsListHeight;
    private float scale, panelLeft, panelTop;
    private float progress, hoverStep, fontScale;
    private int accent;
    private long frameTime, closeAt, changedAt;
    private String toolbarHint;
    private int toolbarHintCenter;
    private Action dragging;
    private KeyMapping captureKey;
    private KeyMapping cachedKey;
    private String cachedKeyLabel;
    private int dragLeft, dragRight;
    private Rect searchBounds = new Rect(0, 0, 0, 0);

    public DashDashboardScreen() {
        this(null);
    }

    DashDashboardScreen(Screen parent) {
        super(Component.literal("Dash Client"));
        this.parent = parent;
        for (int i = 0; i < hits.length; i++) hits[i] = new Hit();
    }

    @Override
    protected void init() {
        uiFont = DashTypography.font(font);
        layout();
        String previous = search == null ? "" : search.getValue();
        search = addWidget(new EditBox(uiFont, 0, 0, Math.max(20, searchBounds.width() - 19), 13,
                searchLabel()));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(TEXT);
        search.setHint(searchLabel());
        placeSearch();
        search.setValue(previous);
        search.setResponder(value -> filter());
        bindings.clear();
        for (var binding : minecraft.options.keyMappings) bindings.put(binding.getName(), binding);
        if (modules.isEmpty()) {
            for (var module : DashClientClient.dashboardModules()) modules.add(new ModuleRow(module));
            for (int i = 0; i < ELEMENTS.length; i++) elementToggles[i] = DashHudLayout.enabled(ELEMENTS[i]) ? 1 : 0;
        }
        filter();
    }

    private void filter() {
        hitCount = 0;
        results.clear();
        String query = search.getValue().trim().toLowerCase(Locale.ROOT);
        for (var row : modules) {
            var module = row.module;
            boolean categoryMatch = category == Nav.ALL || !query.isEmpty() || category.key.equals(module.category());
            if (categoryMatch && (!favoritesOnly || DashAppearance.isFavorite(module.id()))
                    && (query.isEmpty() || module.id().toLowerCase(Locale.ROOT).contains(query)
                    || module.label().get().toLowerCase(Locale.ROOT).contains(query))) {
                results.add(row);
            }
        }
        moduleOffset = 0;
        if (!results.contains(selected)) selected = results.isEmpty() ? null : results.getFirst();
        changedAt = System.nanoTime();
    }

    private void layout() {
        float wanted = DashAppearance.uiScale();
        compact = width / wanted < 537 || height / wanted < 269;
        int minWidth = compact ? 344 : 530;
        panelWidth = Mth.clamp((int) (width / wanted) - 24, minWidth, compact ? 490 : 900);
        panelHeight = Mth.clamp((int) (height / wanted) - 22, compact ? 310 : 284, 576);
        scale = Math.min(wanted, Math.min(Math.max(1, width - 8) / (float) panelWidth,
                Math.max(1, height - 8) / (float) panelHeight));
        int density = minecraft.getWindow().getGuiScale();
        panelLeft = DashTypography.snap((width - panelWidth * scale) * 0.5F, density);
        panelTop = DashTypography.snap((height - panelHeight * scale) * 0.5F, density);
        railWidth = compact ? 92 : 103;
        contentX = 11 + railWidth + GAP;
        contentWidth = panelWidth - contentX - 11;
        int titleWidth = compact ? 62 : Mth.clamp(contentWidth / 4, 80, 125);
        int searchX = contentX + titleWidth;
        int searchW = Math.max(24, panelWidth - 100 - searchX);
        if (searchBounds.x() != searchX || searchBounds.width() != searchW) {
            searchBounds = new Rect(searchX, 15, searchW, 22);
            if (search != null) placeSearch();
        }
        bodyY = compact ? 77 : 50;
        bodyHeight = panelHeight - bodyY - 12;
        if (compact) {
            moduleX = previewX = elementsX = contentX;
            moduleWidth = previewWidth = elementsWidth = contentWidth;
        } else {
            moduleX = contentX;
            moduleWidth = (contentWidth - 2 * GAP) * 36 / 100;
            previewX = moduleX + moduleWidth + GAP;
            previewWidth = (contentWidth - 2 * GAP) * 33 / 100;
            elementsX = previewX + previewWidth + GAP;
            elementsWidth = panelWidth - 11 - elementsX;
        }
    }

    private void placeSearch() {
        search.setX(searchBounds.x() + 15);
        search.setY(searchBounds.y() + 6);
        search.setWidth(Math.max(8, searchBounds.width() - 20));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (DashAppearance.blur()) renderBlurredBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout();
        long now = System.nanoTime();
        long duration = (long) (200_000_000L / Math.max(0.15F, DashAppearance.animationScale()));
        float openedFraction = Mth.clamp((now - opened) / (float) duration, 0, 1);
        float closedFraction = closeAt == 0 ? 0 : Mth.clamp((now - closeAt) / (float) duration, 0, 1);
        progress = ease(openedFraction) * (1 - ease(closedFraction));
        if (closedFraction >= 1) {
            minecraft.setScreen(parent);
            return;
        }
        hoverStep = frameTime == 0 ? 1 : Mth.clamp((now - frameTime) / (duration * .45F), 0.016F, 1);
        frameTime = now;
        fontScale = DashAppearance.fontScale();
        accent = DashAppearance.accent();
        hitCount = 0;
        toolbarHint = null;
        lastMouseX = (int) Math.floor((mouseX - panelLeft) / scale);
        lastMouseY = (int) Math.floor((mouseY - panelTop) / scale);
        graphics.fillGradient(0, 0, width, height, alpha(0xB5000408), alpha(0xE0010205));

        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(panelLeft, panelTop).scale(scale);
            DashShape.fill(graphics, -3, 3, panelWidth + 3, panelHeight + 6, radius(2), alpha(0x80000000));
            DashShape.frame(graphics, 0, 0, panelWidth, panelHeight, radius(0), alpha(EDGE),
                    alpha(DashAppearance.panel(DashAppearance.background())));
            rail(graphics);
            toolbar(graphics, partialTick);
            if (compact) compactTabs(graphics);
            if (!compact || compactSection == 0) function(graphics);
            if (!compact || compactSection == 1) preview(graphics);
            if (!compact || compactSection == 2) elements(graphics);
            drawToolbarHint(graphics);
        } finally {
            pose.popMatrix();
        }
    }

    private void rail(GuiGraphics graphics) {
        int x = 11;
        int right = x + railWidth;
        frame(graphics, x, 11, right, panelHeight - 12, 10, 0xFF11161D);
        DashShape.frame(graphics, x + 7, 19, x + 30, 42, 6, alpha(accent), alpha(0xFF152432));
        icon(graphics, Icon.DASH, x + 12, 24, accent);
        label(graphics, "DASH", x + 36, 20, TEXT, right - x - 42);
        label(graphics, "1.21.11", x + 36, 32, MUTED, right - x - 42);

        int rowY = 52;
        int step = Math.min(23, (panelHeight - 90) / NAV.length);
        for (int i = 0; i < NAV.length; i++) {
            Nav nav = NAV[i];
            if (i == Nav.CONFIGS.ordinal()) {
                graphics.fill(x + 10, rowY, right - 10, rowY + 1, alpha(EDGE));
                rowY += 6;
            }
            boolean active = category == nav;
            boolean hovered = inside(lastMouseX, lastMouseY, x + 5, rowY, right - 5, rowY + step - 1);
            if (active || hovered) fill(graphics, x + 5, rowY, right - 5, rowY + step - 1,
                    active ? 0xFF253640 : 0xFF1B252E, 5);
            if (active) fill(graphics, x + 5, rowY + 2, x + 7, rowY + step - 3, accent, 1);
            icon(graphics, nav.icon, x + 11, rowY + (step - 11) / 2, active ? accent : MUTED);
            label(graphics, nav.label(), x + 27, rowY + (step - 9) / 2, active ? TEXT : MUTED, right - x - 31);
            hit(Action.NAVIGATE, i, x + 5, rowY, right - 5, rowY + step - 1);
            rowY += step;
        }
        int footer = panelHeight - 33;
        button(graphics, x + 7, footer, right - 7, footer + 15,
                text("Призупинити", "Приостановить", "Pause"), Icon.PAUSE, Action.PAUSE, 0);
    }

    private void toolbar(GuiGraphics graphics, float tick) {
        frame(graphics, contentX, 11, panelWidth - 11, 41, radius(-1), 0xFF181F28);
        label(graphics, category.label(), contentX + 8, 21, TEXT, searchBounds.x() - contentX - 12);
        boolean focused = getFocused() == search;
        DashShape.frame(graphics, searchBounds.x(), searchBounds.y(), searchBounds.right(),
                searchBounds.y() + searchBounds.height(), 6, alpha(focused ? accent : EDGE), alpha(0xFF10151C));
        icon(graphics, Icon.SEARCH, searchBounds.x() + 4, searchBounds.y() + 6, MUTED);
        hit(Action.FOCUS_SEARCH, 0, searchBounds.x(), searchBounds.y(), searchBounds.right(), searchBounds.y() + searchBounds.height());
        if (progress > .45F) search.render(graphics, lastMouseX, lastMouseY, tick);
        int start = panelWidth - 96;
        toolbarButton(graphics, start, Icon.STAR, Action.FAVORITES, favoritesOnly,
                text("Обране", "Избранное", "Favorites"));
        toolbarButton(graphics, start + 21, Icon.LANGUAGE, Action.LANGUAGE, false,
                HudSettings.language().displayName());
        toolbarButton(graphics, start + 42, Icon.PALETTE, Action.THEME, false, HudSettings.themeName());
        toolbarButton(graphics, start + 63, Icon.FULL, Action.FULLSCREEN, minecraft.getWindow().isFullscreen(),
                text("На весь екран", "Полный экран", "Fullscreen"));
    }

    private void toolbarButton(GuiGraphics graphics, int x, Icon icon, Action action, boolean active, String hint) {
        boolean hovered = inside(lastMouseX, lastMouseY, x, 16, x + 18, 36);
        fill(graphics, x, 16, x + 18, 36, active ? 0xFF28454F : hovered ? 0xFF283543 : ROW, 5);
        icon(graphics, icon, x + 4, 21, active ? accent : TEXT);
        hit(action, 0, x, 16, x + 18, 36);
        if (hovered) {
            toolbarHint = hint;
            toolbarHintCenter = x + 9;
        }
    }

    private void drawToolbarHint(GuiGraphics graphics) {
        if (toolbarHint == null) return;
        int hintWidth = width(toolbarHint) + 10;
        int hintX = Mth.clamp(toolbarHintCenter - hintWidth / 2, contentX, panelWidth - hintWidth - 12);
        fill(graphics, hintX, 42, hintX + hintWidth, 56, 0xFF293543, 4);
        label(graphics, toolbarHint, hintX + 5, 44, TEXT, hintWidth - 9);
    }

    private void compactTabs(GuiGraphics graphics) {
        int segment = (contentWidth - 2 * GAP) / COMPACT_TITLES.length;
        for (int i = 0; i < COMPACT_TITLES.length; i++) {
            int x = contentX + (segment + GAP) * i;
            fill(graphics, x, 49, x + segment, 69, i == compactSection ? 0xFF2E424D : ROW, 6);
            label(graphics, sectionLabel(i), x + 6, 55, i == compactSection ? accent : TEXT, segment - 10);
            hit(Action.COMPACT_PAGE, i, x, 49, x + segment, 69);
        }
    }

    private void function(GuiGraphics graphics) {
        int right = moduleX + moduleWidth;
        section(graphics, moduleX, right, sectionLabel(0), Icon.GRID, Integer.toString(results.size()));
        moduleListY = bodyY + 29;
        int bottom = bodyY + bodyHeight - 6;
        int inspectorHeight = selected == null ? 0 : inspectorHeight(selected.module);
        int inspectorY = bottom - inspectorHeight;
        moduleListHeight = inspectorY - moduleListY - 5;
        int visible = Math.max(1, moduleListHeight / ROW_HEIGHT);
        moduleOffset = Mth.clamp(moduleOffset, 0, Math.max(0, results.size() - visible));
        graphics.enableScissor(moduleX + 3, moduleListY, right - 3, moduleListY + moduleListHeight);
        try {
            if (results.isEmpty()) {
                label(graphics, text("Нічого не знайдено", "Ничего не найдено", "No matching modules"),
                        moduleX + 8, moduleListY + 5, MUTED, moduleWidth - 16);
            }
            int limit = Math.min(results.size(), moduleOffset + visible + 1);
            for (int i = moduleOffset; i < limit; i++) {
                ModuleRow row = results.get(i);
                int y = moduleListY + (i - moduleOffset) * ROW_HEIGHT;
                moduleRow(graphics, row, i, moduleX + 6, y, right - 6);
            }
        } finally {
            graphics.disableScissor();
        }
        scrollbar(graphics, right - 4, moduleListY, moduleListHeight, results.size(), visible, moduleOffset);
        if (selected != null) inspector(graphics, selected.module, moduleX + 6, inspectorY, right - 6, bottom);
    }

    private void moduleRow(GuiGraphics graphics, ModuleRow row, int index, int x, int y, int right) {
        int clipped = Math.min(y + ROW_HEIGHT - 3, moduleListY + moduleListHeight);
        boolean hovering = inside(lastMouseX, lastMouseY, x, y, right, clipped);
        row.hover = Mth.lerp(hoverStep, row.hover, hovering ? 1 : 0);
        boolean on = row.module.enabled().getAsBoolean();
        row.toggle = Mth.lerp(hoverStep, row.toggle, on ? 1 : 0);
        int base = ARGB.srgbLerp(row.hover, row == selected ? 0xFF273742 : ROW, 0xFF32424E);
        fill(graphics, x, y, right, y + 20, base, 5);
        if (on) fill(graphics, x + 1, y + 4, x + 3, y + 16, accent, 1);
        int switchX = right - 26;
        int starX = switchX - 16;
        label(graphics, row.module.label().get(), x + 7, y + 6, on ? TEXT : MUTED, starX - x - 8);
        icon(graphics, Icon.STAR, starX + 1, y + 5, DashAppearance.isFavorite(row.module.id()) ? accent : 0xFF737E8E);
        drawSwitch(graphics, switchX, y + 5, row.toggle);
        hit(Action.SELECT_MODULE, index, x, y, starX, clipped);
        hit(Action.STAR_MODULE, index, starX, y, switchX, clipped);
        hit(Action.TOGGLE_MODULE, index, switchX, y, right, clipped);
    }

    private int inspectorHeight(DashClientClient.DashboardModule module) {
        int height = switch (module.id()) {
            case "Trigger Bot" -> 79;
            case "Velocity" -> 66;
            default -> module.configure() == null ? 48 : 62;
        };
        return bindingFor(module) == null ? height : height + 20;
    }

    private KeyMapping bindingFor(DashClientClient.DashboardModule module) {
        return switch (module.id()) {
            case "Trigger Bot" -> bindings.get("key.dash-client.trigger_bot");
            case "Aim Assist" -> bindings.get("key.dash-client.aim-assist");
            case "Freelook" -> bindings.get("key.dash-client.freelook");
            case "Marks" -> bindings.get("key.dash-client.marks");
            case "Zoom" -> bindings.get("key.dash-client.zoom");
            default -> null;
        };
    }

    private void inspector(GuiGraphics graphics, DashClientClient.DashboardModule module, int x, int y, int right, int bottom) {
        DashAppearance.drawPanel(graphics, x, y, right, bottom, alpha(0xFF18242E), module.enabled().getAsBoolean());
        label(graphics, module.label().get(), x + 6, y + 7, TEXT, right - x - 12);
        if (module.id().equals("Trigger Bot")) {
            var range = (float) ((TriggerBot.range() - TriggerBot.MIN_RANGE) / (TriggerBot.MAX_RANGE - TriggerBot.MIN_RANGE));
            String blocks = Math.round(TriggerBot.range() * 10) / 10F + text(" бл.", " бл.", " blk.");
            slider(graphics, x + 6, y + 23, right - 6, range, blocks,
                    text("Дальність", "Дальность", "Range"), Action.TRIGGER_RANGE);
            int half = (right - x - 16) / 2;
            for (int i = 0; i < TARGETS.length; i++) {
                int left = x + 6 + (i % 2) * (half + 4);
                int top = y + 43 + (i / 2) * 16;
                boolean on = TriggerBot.targets(TARGETS[i]);
                fill(graphics, left, top, left + half, top + 14, on ? 0xFF304955 : ROW, 4);
                label(graphics, targetLabel(TARGETS[i]), left + 4, top + 3, on ? accent : MUTED, half - 6);
                hit(Action.TRIGGER_TARGET, i, left, top, left + half, top + 14);
            }
        } else if (module.id().equals("Velocity")) {
            slider(graphics, x + 6, y + 28, right - 6, Velocity.force(), percentage(Velocity.force()),
                    text("Відкидання", "Отбрасывание", "Knockback"), Action.VELOCITY);
        } else if (module.configure() != null) {
            label(graphics, module.category(), x + 6, y + 22, MUTED, right - x - 12);
            button(graphics, x + 6, y + 36, right - 6, y + 55,
                    text("Налаштувати", "Настроить", "Configure"), Icon.SLIDERS, Action.CONFIGURE, 0);
        } else {
            label(graphics, module.enabled().getAsBoolean() ? text("Увімкнено", "Включено", "Enabled")
                    : text("Вимкнено", "Выключено", "Disabled"), x + 6, y + 25,
                    module.enabled().getAsBoolean() ? accent : MUTED, right - x - 12);
        }
        KeyMapping binding = bindingFor(module);
        if (binding != null) {
            if (cachedKey != binding) {
                cachedKey = binding;
                cachedKeyLabel = binding.getTranslatedKeyMessage().getString();
            }
            int top = bottom - 21;
            button(graphics, x + 6, top, right - 6, bottom - 5,
                    captureKey == binding ? text("Натисни клавішу", "Нажми клавишу", "Press a key")
                            : text("Клавіша ", "Клавиша ", "Key ") + cachedKeyLabel,
                    null, Action.KEY_CAPTURE, 0);
        }
    }

    private static String targetLabel(TriggerBot.TargetType type) {
        return switch (type) {
            case PLAYERS -> text("Гравці", "Игроки", "Players");
            case HOSTILE -> text("Ворожі", "Враждебные", "Hostile");
            case NEUTRAL -> text("Нейтральні", "Нейтральные", "Neutral");
            case PASSIVE -> text("Мирні", "Мирные", "Passive");
        };
    }

    private void preview(GuiGraphics graphics) {
        int right = previewX + previewWidth;
        section(graphics, previewX, right, sectionLabel(1), Icon.PREVIEW, null);
        int x = previewX + 7;
        int end = right - 7;
        int tileY = bodyY + 30;
        int sampleHeight = Math.min(92, Math.max(56, bodyHeight - 167));
        DashAppearance.drawPanel(graphics, x, tileY, end, tileY + sampleHeight, alpha(0xFF17232E), false);
        graphics.enableScissor(x + 2, tileY + 2, end - 2, tileY + sampleHeight - 2);
        try {
            if (DashHudLayout.enabled(DashHudLayout.Widget.WATERMARK)) {
                fill(graphics, x + 6, tileY + 7, end - 6, tileY + 27, HudSettings.color(0xFF15202A), 5);
                icon(graphics, Icon.DASH, x + 10, tileY + 12, accent);
                label(graphics, "Dash Client", x + 25, tileY + 13, TEXT, end - x - 32);
            }
            if (DashHudLayout.enabled(DashHudLayout.Widget.FPS)) {
                int valueWidth = 52;
                int scaled = Math.round(valueWidth * HudSettings.scale());
                int left = end - 8 - scaled;
                fill(graphics, left, tileY + 34, end - 7, tileY + 51, HudSettings.color(0xFF16222D), 5);
                label(graphics, minecraft.getFps() + " FPS", left + 4, tileY + 39, accent, scaled - 6);
            }
            if (sampleHeight >= 69 && DashHudLayout.enabled(DashHudLayout.Widget.TARGET)) {
                fill(graphics, x + 6, tileY + sampleHeight - 23, end - 6, tileY + sampleHeight - 7,
                        HudSettings.color(0xFF192531), 5);
                fill(graphics, x + 10, tileY + sampleHeight - 17, end - 26, tileY + sampleHeight - 13, accent, 2);
            }
        } finally {
            graphics.disableScissor();
        }

        int y = tileY + sampleHeight + 7;
        button(graphics, x, y, end, y + 19, HudSettings.themeName(), Icon.PALETTE, Action.THEME, 0);
        y += 25;
        for (int i = 0; i < SWATCHES.length; i++) {
            int sx = x + 3 + i * 17;
            if (sx + 13 > end) break;
            boolean active = (accent & 0xFFFFFF) == (SWATCHES[i] & 0xFFFFFF);
            DashShape.frame(graphics, sx, y, sx + 13, y + 13, 6, alpha(active ? TEXT : EDGE), alpha(SWATCHES[i]));
            hit(Action.ACCENT, i, sx - 1, y - 2, sx + 15, y + 15);
        }
        y += 19;
        slider(graphics, x + 2, y, end - 2, HudSettings.opacitySlider(), percentage(HudSettings.opacity()),
                text("Прозорість HUD", "Прозрачность HUD", "HUD opacity"), Action.HUD_OPACITY);
        y += 21;
        slider(graphics, x + 2, y, end - 2, HudSettings.scaleSlider(), percentage(HudSettings.scale()),
                text("Масштаб HUD", "Масштаб HUD", "HUD scale"), Action.HUD_SCALE);
        y += 23;
        int half = (end - x - 4) / 2;
        button(graphics, x, y, x + half, y + 16, DashAppearance.blur() ? "Blur ✓" : "Blur",
                null, Action.BLUR, 0);
        button(graphics, x + half + 4, y, end, y + 16, DashAppearance.glass() ? "Glass ✓" : "Glass",
                null, Action.GLASS, 0);
        y += 20;
        button(graphics, x, y, end, y + 17, text("Редактор стилю", "Редактор стиля", "Style editor"),
                Icon.SLIDERS, Action.APPEARANCE, 0);
    }

    private void elements(GuiGraphics graphics) {
        int right = elementsX + elementsWidth;
        int enabled = 0;
        for (var element : ELEMENTS) if (DashHudLayout.enabled(element)) enabled++;
        section(graphics, elementsX, right, sectionLabel(2), Icon.ELEMENTS, enabled + "/" + ELEMENTS.length);
        elementsListY = bodyY + 29;
        var bottom = bodyY + bodyHeight - 7;
        elementsListHeight = bottom - 24 - elementsListY;
        int step = 21;
        int visible = Math.max(1, elementsListHeight / step);
        hudOffset = Mth.clamp(hudOffset, 0, Math.max(0, ELEMENTS.length - visible));
        graphics.enableScissor(elementsX + 3, elementsListY, right - 3, elementsListY + elementsListHeight);
        try {
            int limit = Math.min(ELEMENTS.length, hudOffset + visible + 1);
            for (int i = hudOffset; i < limit; i++) {
                int x = elementsX + 6;
                int end = right - 6;
                int y = elementsListY + (i - hudOffset) * step;
                int clipped = Math.min(y + 18, elementsListY + elementsListHeight);
                boolean enabledElement = DashHudLayout.enabled(ELEMENTS[i]);
                boolean hover = inside(lastMouseX, lastMouseY, x, y, end, clipped);
                fill(graphics, x, y, end, y + 18, hover ? 0xFF283642 : ROW, 4);
                fill(graphics, x + 4, y + 7, x + 7, y + 11, enabledElement ? accent : 0xFF51606D, 1);
                label(graphics, ELEMENTS[i].label(), x + 10, y + 5, enabledElement ? TEXT : MUTED, end - x - 47);
                icon(graphics, Icon.SLIDERS, end - 39, y + 4, MUTED);
                elementToggles[i] = Mth.lerp(hoverStep, elementToggles[i], enabledElement ? 1 : 0);
                drawSwitch(graphics, end - 25, y + 4, elementToggles[i]);
                hit(Action.EDIT_ELEMENT, i, x, y, end - 26, clipped);
                hit(Action.TOGGLE_ELEMENT, i, end - 26, y, end, clipped);
            }
        } finally {
            graphics.disableScissor();
        }
        scrollbar(graphics, right - 4, elementsListY, elementsListHeight, ELEMENTS.length, visible, hudOffset);
        button(graphics, elementsX + 6, bottom - 19, right - 6, bottom,
                text("Розташувати HUD", "Разместить HUD", "Arrange HUD"), Icon.FULL, Action.HUD_EDITOR, 0);
    }

    private void section(GuiGraphics graphics, int x, int right, String title, Icon icon, String badge) {
        frame(graphics, x, bodyY, right, bodyY + bodyHeight, radius(-1), 0xFF141A22);
        fill(graphics, x + 4, bodyY + 4, right - 4, bodyY + 23, 0xFF202B37, 5);
        icon(graphics, icon, x + 8, bodyY + 8, accent);
        int reserved = badge == null ? 14 : width(badge) + 18;
        label(graphics, title, x + 25, bodyY + 9, TEXT, right - x - 25 - reserved);
        if (badge != null) label(graphics, badge, right - width(badge) - 10, bodyY + 9, MUTED, reserved - 8);
        long elapsed = frameTime - changedAt;
        float line = 1 - Mth.clamp(elapsed / 230_000_000F, 0, 1);
        if (line > 0) fill(graphics, x + 6, bodyY + 23, x + 6 + Math.round((right - x - 12) * line), bodyY + 24, accent, 1);
    }

    private static String sectionLabel(int section) {
        return switch (section) {
            case 0 -> text("Модулі", "Модули", COMPACT_TITLES[0]);
            case 1 -> text("Вигляд", "Оформление", COMPACT_TITLES[1]);
            default -> text("Елементи", "Элементы", COMPACT_TITLES[2]);
        };
    }

    private void slider(GuiGraphics graphics, int x, int y, int right, float amount, String value,
                        String name, Action action) {
        int handle = x + Math.round((right - x) * amount);
        int valueWidth = width(value);
        label(graphics, name, x, y, MUTED, right - x - valueWidth - 4);
        label(graphics, value, right - valueWidth, y, TEXT, valueWidth + 2);
        fill(graphics, x, y + 13, right, y + 16, 0xFF3D4956, 1);
        fill(graphics, x, y + 13, handle, y + 16, accent, 1);
        fill(graphics, handle - 2, y + 11, handle + 3, y + 18, TEXT, 2);
        hit(action, 0, x, y + 8, right + 1, y + 20);
    }

    private static String percentage(float amount) {
        return Math.round(amount * 100) + "%";
    }

    private void drawSwitch(GuiGraphics graphics, int x, int y, float amount) {
        int color = ARGB.srgbLerp(amount, 0xFF34414D, accent);
        fill(graphics, x, y, x + 22, y + 10, color, 5);
        int knob = x + 2 + Math.round(12 * amount);
        fill(graphics, knob, y + 2, knob + 6, y + 8, TEXT, 3);
    }

    private void button(GuiGraphics graphics, int x, int y, int right, int bottom,
                        String label, Icon icon, Action action, int data) {
        boolean hovered = inside(lastMouseX, lastMouseY, x, y, right, bottom);
        fill(graphics, x, y, right, bottom, hovered ? 0xFF314450 : ROW, 5);
        if (icon != null) icon(graphics, icon, x + 5, y + (bottom - y - 11) / 2, hovered ? accent : MUTED);
        int textX = x + (icon == null ? 5 : 19);
        label(graphics, label, textX, y + (bottom - y - 9) / 2, TEXT, right - textX - 4);
        hit(action, data, x, y, right, bottom);
    }

    private void scrollbar(GuiGraphics graphics, int x, int y, int height, int count, int visible, int offset) {
        if (height <= 0 || count <= visible) return;
        int handle = Math.max(9, height * visible / count);
        int offsetY = (height - handle) * offset / (count - visible);
        fill(graphics, x, y + offsetY, x + 2, y + offsetY + handle, 0xFF536879, 1);
    }

    private void frame(GuiGraphics graphics, int x, int y, int right, int end, int radius, int color) {
        DashShape.frame(graphics, x, y, right, end, radius, alpha(EDGE), alpha(DashAppearance.panel(color)));
    }

    private void fill(GuiGraphics graphics, int x, int y, int right, int end, int color, int radius) {
        DashShape.fill(graphics, x, y, right, end, radius, alpha(color));
    }

    private int alpha(int argb) {
        return Math.round((argb >>> 24) * progress) << 24 | argb & 0xFFFFFF;
    }

    private static float ease(float value) {
        float remaining = 1 - value;
        return 1 - remaining * remaining * remaining;
    }

    private int radius(int offset) {
        return Math.max(2, DashAppearance.cornerRadius() + offset);
    }

    private int width(String text) {
        return Math.round(uiFont.width(text) * fontScale);
    }

    private void label(GuiGraphics graphics, String value, int x, int y, int color, int maxWidth) {
        if (maxWidth <= 0) return;
        String fitted = DashHud.fit(uiFont, value, (int) (maxWidth / fontScale));
        if (Math.abs(fontScale - 1) < .01F) {
            graphics.drawString(uiFont, fitted, x, y, alpha(color), false);
        } else {
            var pose = graphics.pose();
            pose.pushMatrix();
            try {
                pose.translate(x, y).scale(fontScale);
                graphics.drawString(uiFont, fitted, 0, 0, alpha(color), false);
            } finally {
                pose.popMatrix();
            }
        }
    }

    private void icon(GuiGraphics graphics, Icon icon, int x, int y, int color) {
        DashDashboardIcons.draw(graphics, icon, x, y, alpha(color));
    }

    private void hit(Action action, int data, int x, int y, int right, int bottom) {
        if (x >= right || y >= bottom || hitCount >= hits.length) return;
        Hit hit = hits[hitCount++];
        hit.action = action;
        hit.data = data;
        hit.x = x;
        hit.y = y;
        hit.right = right;
        hit.bottom = bottom;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closeAt != 0) return true;
        captureKey = null;
        double x = localX(event.x()), y = localY(event.y());
        if (search.mouseClicked(new MouseButtonEvent(x, y, event.buttonInfo()), doubleClick)) {
            setFocused(search);
            return true;
        }
        setFocused(null);
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            for (int i = hitCount - 1; i >= 0; i--) {
                Hit hit = hits[i];
                if ((hit.action == Action.SELECT_MODULE || hit.action == Action.TOGGLE_MODULE)
                        && inside(x, y, hit.x, hit.y, hit.right, hit.bottom)) {
                    selected = results.get(hit.data);
                    Runnable configure = selected.module.configure();
                    if (configure != null) configure.run();
                    return true;
                }
            }
            return false;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (int i = hitCount - 1; i >= 0; i--) {
            Hit hit = hits[i];
            if (!inside(x, y, hit.x, hit.y, hit.right, hit.bottom)) continue;
            activate(hit, x);
            return true;
        }
        return false;
    }

    private void activate(Hit hit, double x) {
        switch (hit.action) {
            case NAVIGATE -> navigate(NAV[hit.data]);
            case PAUSE -> SaveMode.pause(minecraft);
            case FAVORITES -> { favoritesOnly = !favoritesOnly; filter(); }
            case LANGUAGE -> {
                HudSettings.setLanguage(LANGUAGES[(HudSettings.language().ordinal() + 1) % LANGUAGES.length]);
                search.setHint(searchLabel());
                filter();
            }
            case THEME -> DashAppearance.cyclePreset();
            case FULLSCREEN -> {
                minecraft.getWindow().toggleFullScreen();
                minecraft.options.fullscreen().set(minecraft.getWindow().isFullscreen());
                minecraft.options.save();
            }
            case FOCUS_SEARCH -> focusSearch();
            case COMPACT_PAGE -> { compactSection = hit.data; changedAt = System.nanoTime(); }
            case SELECT_MODULE -> selected = results.get(hit.data);
            case STAR_MODULE -> {
                DashAppearance.toggleFavorite(results.get(hit.data).module.id());
                if (favoritesOnly) filter();
            }
            case TOGGLE_MODULE -> {
                selected = results.get(hit.data);
                selected.module.toggle().run();
            }
            case CONFIGURE -> {
                if (selected != null && selected.module.configure() != null) selected.module.configure().run();
            }
            case KEY_CAPTURE -> {
                if (selected != null) captureKey = bindingFor(selected.module);
            }
            case TRIGGER_TARGET -> { TriggerBot.toggleTarget(TARGETS[hit.data]); TriggerBot.saveSettings(); }
            case TRIGGER_RANGE, VELOCITY, HUD_OPACITY, HUD_SCALE -> {
                dragging = hit.action;
                dragLeft = hit.x;
                dragRight = hit.right - 1;
                dragTo(x);
            }
            case ACCENT -> DashAppearance.setAccent(SWATCHES[hit.data]);
            case BLUR -> { HudSettings.toggleBlur(); HudSettings.save(); }
            case GLASS -> DashAppearance.setGlass(!DashAppearance.glass());
            case APPEARANCE -> minecraft.setScreen(new DashAppearanceScreen(this));
            case HUD_EDITOR -> minecraft.setScreen(new DashHudEditor(this));
            case EDIT_ELEMENT -> minecraft.setScreen(new DashHudEditor(this, ELEMENTS[hit.data]));
            case TOGGLE_ELEMENT -> DashHudLayout.toggle(ELEMENTS[hit.data]);
        }
    }

    private void navigate(Nav nav) {
        switch (nav) {
            case CONFIGS, FRIENDS, WAYPOINTS -> minecraft.setScreen(new DashLibraryScreen(this, nav.key));
            case SETTINGS -> minecraft.setScreen(new DashAppearanceScreen(this));
            case SEARCH -> {
                category = Nav.ALL;
                compactSection = 0;
                filter();
                focusSearch();
            }
            default -> {
                category = nav;
                compactSection = 0;
                search.setValue("");
                filter();
            }
        }
    }

    private void focusSearch() {
        setFocused(search);
        search.setCursorPosition(search.getValue().length());
    }

    private static Component searchLabel() {
        return Component.literal(text("Пошук модулів", "Поиск модулей", "Search modules"));
    }

    private void dragTo(double x) {
        float amount = Mth.clamp((float) (x - dragLeft) / Math.max(1, dragRight - dragLeft), 0, 1);
        switch (dragging) {
            case TRIGGER_RANGE -> TriggerBot.setRange(Mth.lerp(amount, TriggerBot.MIN_RANGE, TriggerBot.MAX_RANGE));
            case VELOCITY -> Velocity.setForce(amount);
            case HUD_OPACITY -> HudSettings.setOpacitySlider(amount);
            case HUD_SCALE -> HudSettings.setScaleSlider(amount);
            default -> {}
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null) {
            dragTo(localX(event.x()));
            return true;
        }
        if (getFocused() == search) return search.mouseDragged(
                new MouseButtonEvent(localX(event.x()), localY(event.y()), event.buttonInfo()), dx / scale, dy / scale);
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            TriggerBot.saveSettings();
            HudSettings.save();
            return true;
        }
        return super.mouseReleased(new MouseButtonEvent(localX(event.x()), localY(event.y()), event.buttonInfo()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || closeAt != 0) return false;
        double x = localX(mouseX), y = localY(mouseY);
        int direction = scrollY > 0 ? -1 : 1;
        if ((!compact || compactSection == 0) && inside(x, y, moduleX, moduleListY, moduleX + moduleWidth, moduleListY + moduleListHeight)) {
            moduleOffset = Mth.clamp(moduleOffset + 2 * direction, 0, Math.max(0, results.size() - Math.max(1, moduleListHeight / ROW_HEIGHT)));
            return true;
        }
        if ((!compact || compactSection == 2) && inside(x, y, elementsX, elementsListY, elementsX + elementsWidth, elementsListY + elementsListHeight)) {
            hudOffset = Mth.clamp(hudOffset + 2 * direction, 0, Math.max(0, ELEMENTS.length - Math.max(1, elementsListHeight / 21)));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closeAt != 0) return true;
        if (captureKey != null) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE || event.key() == GLFW.GLFW_KEY_DELETE) {
                InventoryBindSupport.unbind(minecraft, captureKey);
            } else if (event.key() != GLFW.GLFW_KEY_ESCAPE && !InventoryBindSupport.assign(minecraft, captureKey, event)) {
                DashHudLayout.notify(text("Клавіша зайнята", "Клавиша занята", "Key unavailable"),
                        text("Вибери іншу клавішу", "Выбери другую клавишу", "Choose another key"));
                return true;
            }
            captureKey = null;
            cachedKey = null;
            return true;
        }
        if (DashClientClient.dashboardKey(event)) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_K && (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0) {
            focusSearch();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (closeAt == 0) closeAt = System.nanoTime(); }
    @Override public void removed() { HudSettings.save(); TriggerBot.saveSettings(); }

    boolean compactLayout() { return compact; }
    int visibleModules() { return results.size(); }
    private double localX(double x) { return (x - panelLeft) / scale; }
    private double localY(double y) { return (y - panelTop) / scale; }

    private static boolean inside(double x, double y, int left, int top, int right, int bottom) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    enum Icon { DASH, GRID, COMBAT, MOVE, PLAYER, RENDER, VISUAL, CLIENT, CONFIG, FRIEND, PIN, SLIDERS,
        SEARCH, STAR, LANGUAGE, PALETTE, FULL, PREVIEW, ELEMENTS, PAUSE }

    private enum Nav {
        ALL("All", "Усе", "Все", Icon.GRID),
        COMBAT("Combat", "Бій", "Бой", Icon.COMBAT),
        MOVEMENT("Movement", "Рух", "Движение", Icon.MOVE),
        PLAYER("Player", "Гравець", "Игрок", Icon.PLAYER),
        RENDER("Render", "Рендер", "Рендер", Icon.RENDER),
        VISUAL("Visual", "Візуал", "Визуал", Icon.VISUAL),
        CLIENT("Client", "Клієнт", "Клиент", Icon.CLIENT),
        CONFIGS("Configs", "Конфіги", "Конфиги", Icon.CONFIG),
        FRIENDS("Friends", "Друзі", "Друзья", Icon.FRIEND),
        WAYPOINTS("Waypoints", "Мітки", "Метки", Icon.PIN),
        SETTINGS("Settings", "Параметри", "Настройки", Icon.SLIDERS),
        SEARCH("Search", "Пошук", "Поиск", Icon.SEARCH);

        private final String key, ukrainian, russian;
        private final Icon icon;

        Nav(String key, String ukrainian, String russian, Icon icon) {
            this.key = key;
            this.ukrainian = ukrainian;
            this.russian = russian;
            this.icon = icon;
        }

        String label() { return text(ukrainian, russian, key); }
    }

    private enum Action { NAVIGATE, PAUSE, FAVORITES, LANGUAGE, THEME, FULLSCREEN, FOCUS_SEARCH, COMPACT_PAGE,
        SELECT_MODULE, STAR_MODULE, TOGGLE_MODULE, CONFIGURE, KEY_CAPTURE, TRIGGER_TARGET, TRIGGER_RANGE, VELOCITY,
        ACCENT, BLUR, GLASS, HUD_OPACITY, HUD_SCALE, APPEARANCE, HUD_EDITOR, EDIT_ELEMENT, TOGGLE_ELEMENT }

    private static final class ModuleRow {
        private final DashClientClient.DashboardModule module;
        private float hover, toggle;
        private ModuleRow(DashClientClient.DashboardModule module) {
            this.module = module;
            toggle = module.enabled().getAsBoolean() ? 1 : 0;
        }
    }

    private static final class Hit {
        Action action;
        int data, x, y, right, bottom;
    }

    private record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
    }
}
