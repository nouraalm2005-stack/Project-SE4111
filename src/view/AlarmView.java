package view;

import model.Alarm;
import model.AlarmObserver;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Swing View and concrete Observer (animated edition).
 *
 * This class contains presentation only. User actions are forwarded to the controller
 * through the setXxxAction handlers, and all alarm data is supplied by the model through
 * the AlarmObserver callbacks. All animation (sky colours, analog clock, card transitions,
 * switches, pulsing rings) is pure drawing code driven by javax.swing.Timer, so no
 * external library is needed and the model/controller are unchanged.
 */
public class AlarmView extends JFrame implements AlarmObserver {

    public record AlarmInput(LocalTime time, String label, Set<DayOfWeek> days) { }

    // ------------------------------------------------------------------ theme
    private static final String FONT = pickFont();
    private static final Color BACKGROUND = new Color(243, 244, 251);
    private static final Color WHITE = Color.WHITE;
    private static final Color INK = new Color(35, 39, 67);
    private static final Color MUTED = new Color(125, 131, 155);
    private static final Color PRIMARY = new Color(107, 94, 237);
    private static final Color PRIMARY_DARK = new Color(86, 72, 208);
    private static final Color PRIMARY_PALE = new Color(239, 236, 255);
    private static final Color BORDER = new Color(228, 229, 241);
    private static final Color MINT = new Color(30, 190, 150);
    private static final Color ROSE = new Color(222, 82, 112);
    private static final Color ROSE_PALE = new Color(255, 236, 241);
    private static final Color AMBER = new Color(255, 176, 64);
    private static final Color STRIPE = new Color(247, 247, 252);

    private static String pickFont() {
        Set<String> names = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "SF Pro Display", "Helvetica Neue", "Inter", "Arial"}) {
            if (names.contains(f)) return f;
        }
        return Font.SANS_SERIF;
    }

    private static Font font(int style, float size) { return new Font(FONT, style, 1).deriveFont(style, size); }

    private static final Font BODY = font(Font.PLAIN, 14f);
    private static final Font BODY_BOLD = font(Font.BOLD, 14f);
    private static final Font SMALL = font(Font.PLAIN, 12f);
    private static final Font HEADING = font(Font.BOLD, 26f);

    // ------------------------------------------------------------------ state
    private final Hero hero = new Hero();
    private final AlarmList list = new AlarmList();
    private final Badge countBadge = new Badge();

    private final StyleButton addBtn = new StyleButton("+  Add alarm", PRIMARY, WHITE, PRIMARY_DARK, 14, 24, true);
    private final StyleButton editBtn = new StyleButton("Edit", PRIMARY_PALE, PRIMARY_DARK, new Color(222, 216, 255), 14, 22, false);
    private final StyleButton deleteBtn = new StyleButton("Delete", ROSE_PALE, ROSE, new Color(255, 216, 227), 14, 22, false);

    private List<Alarm> shown = new ArrayList<>();
    private Consumer<Alarm> snoozeHandler = a -> { };
    private Runnable toggleAction = () -> { };
    private Runnable editAction = () -> { };
    private Runnable deleteAction = () -> { };

    // clock information pushed by the controller once per second (smoothed by the hero)
    private LocalTime lastNow = LocalTime.of(12, 0);
    private LocalDate lastDate = LocalDate.now();
    private long lastNanos = System.nanoTime();
    private String nextLabel = null;
    private String nextIn = "";
    private int nextId = -1;

    public AlarmView() {
        super("Alarm Clock");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(appIcon());
        setMinimumSize(new Dimension(960, 660));
        setSize(1080, 720);
        getContentPane().setBackground(BACKGROUND);
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new BorderLayout(26, 0));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(24, 24, 24, 26));
        content.add(hero, BorderLayout.WEST);
        content.add(createRightSide(), BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);
        setLocationRelativeTo(null);
    }

    private JComponent createRightSide() {
        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setOpaque(false);

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(6, 8, 0, 6));
        JPanel texts = column();
        texts.add(label("Your alarms", HEADING, INK));
        texts.add(Box.createVerticalStrut(4));
        JLabel sub = label("Flip a switch to turn an alarm on or off.", BODY, MUTED);
        sub.setBorder(new EmptyBorder(0, 0, 0, 16));
        texts.add(sub);
        heading.add(texts, BorderLayout.WEST);
        JPanel badgeBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        badgeBox.setOpaque(false);
        badgeBox.add(countBadge);
        heading.add(badgeBox, BorderLayout.EAST);
        right.add(heading, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setViewportBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getVerticalScrollBar().setUI(new SlimScrollUI());
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        right.add(scroll, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(2, 8, 2, 8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(addBtn);
        buttons.add(editBtn);
        buttons.add(deleteBtn);
        bar.add(buttons, BorderLayout.WEST);
        bar.add(label("Double-click a card to edit", SMALL, MUTED), BorderLayout.EAST);
        right.add(bar, BorderLayout.SOUTH);
        return right;
    }

    // ------------------------------------------------- controller wiring only
    public void setAddAction(Runnable action) { addBtn.addActionListener(e -> action.run()); }
    public void setEditAction(Runnable action) { editAction = action; editBtn.addActionListener(e -> action.run()); }
    public void setDeleteAction(Runnable action) { deleteAction = action; deleteBtn.addActionListener(e -> action.run()); }
    public void setToggleAction(Runnable action) { toggleAction = action; }
    public void setSnoozeHandler(Consumer<Alarm> handler) { snoozeHandler = handler; }

    // ------------------------------------------------------- Observer methods
    @Override
    public void onAlarmsChanged(List<Alarm> alarms) {
        shown = new ArrayList<>(alarms);
        list.setAlarms(shown);
        countBadge.setCount(alarms.size());
        updateNext();
        hero.repaint();
    }

    public Alarm getSelectedAlarm() {
        Integer id = list.selectedId;
        if (id == null) return null;
        for (Alarm a : shown) if (a.getId() == id) return a;
        return null;
    }

    public void showTime(LocalTime now) {
        lastNow = now;
        lastDate = LocalDate.now();
        lastNanos = System.nanoTime();
        updateNext();
    }

    @Override
    public void onAlarmRinging(Alarm alarm) {
        showRingingDialog(alarm);
    }

    public void showError(String message) {
        JLabel text = label(message, BODY, INK);
        text.setBorder(new EmptyBorder(10, 12, 10, 12));
        JOptionPane.showMessageDialog(this, text, "Check alarm details", JOptionPane.WARNING_MESSAGE);
    }

    // --------------------------------------------------------- next-alarm info
    private LocalDateTime nextOccurrence(LocalTime t, Set<DayOfWeek> days) {
        LocalDateTime base = LocalDateTime.of(lastDate, lastNow.withNano(0));
        for (int d = 0; d <= 7; d++) {
            LocalDate date = lastDate.plusDays(d);
            if (!days.isEmpty() && !days.contains(date.getDayOfWeek())) continue;
            LocalDateTime c = LocalDateTime.of(date, t);
            if (c.isAfter(base)) return c;
        }
        return null;
    }

    private String untilText(LocalDateTime when) {
        Duration d = Duration.between(LocalDateTime.of(lastDate, lastNow.withNano(0)), when);
        long m = (d.getSeconds() + 59) / 60;
        if (m <= 0) return "less than a minute";
        long days = m / 1440, h = (m % 1440) / 60, mm = m % 60;
        if (days > 0) return days + "d " + h + "h";
        if (h > 0) return h + "h " + mm + "m";
        return mm + " min";
    }

    private void updateNext() {
        LocalDateTime best = null;
        Alarm bestAlarm = null;
        for (Alarm a : shown) {
            if (!a.isEnabled()) continue;
            LocalDateTime c = nextOccurrence(a.getTime(), a.getRepeatDays());
            if (c != null && (best == null || c.isBefore(best))) { best = c; bestAlarm = a; }
        }
        if (best == null) { nextLabel = null; nextIn = ""; nextId = -1; }
        else { nextLabel = bestAlarm.getLabel(); nextIn = untilText(best); nextId = bestAlarm.getId(); }
    }

    // ================================================================== dialogs
    private void showRingingDialog(Alarm alarm) {
        JDialog dialog = new JDialog(this, "Alarm ringing", false);
        dialog.setUndecorated(true);
        RingPanel panel = new RingPanel(alarm);
        panel.setLayout(new BorderLayout());
        JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(0, 30, 28, 30));
        StyleButton dismiss = new StyleButton("Dismiss", new Color(255, 255, 255, 46), WHITE, new Color(255, 255, 255, 84), 14, 20, false);
        StyleButton snooze = new StyleButton("Snooze 5 min", WHITE, new Color(70, 52, 170), new Color(236, 232, 255), 14, 20, false);
        Chime chime = new Chime();                    // soft bell melody, repeats until dismissed
        dismiss.addActionListener(e -> dialog.dispose());
        snooze.addActionListener(e -> { snoozeHandler.accept(alarm); dialog.dispose(); });
        actions.add(dismiss);
        actions.add(snooze);
        panel.add(actions, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.setSize(440, 470);
        dialog.setLocationRelativeTo(this);
        dialog.setAlwaysOnTop(true);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { chime.stop(); panel.stop(); }
        });
        MouseAdapter drag = new MouseAdapter() {
            Point start;
            @Override public void mousePressed(MouseEvent e) { start = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                Point p = dialog.getLocation();
                dialog.setLocation(p.x + e.getX() - start.x, p.y + e.getY() - start.y);
            }
        };
        panel.addMouseListener(drag);
        panel.addMouseMotionListener(drag);
        chime.start();
        panel.start();
        dialog.setVisible(true);
    }

    /** @return form data, or null if the dialog is cancelled. */
    public AlarmInput askAlarmInput(Alarm existing) {
        LocalTime t = existing != null ? existing.getTime() : LocalTime.now().plusMinutes(1);
        JLabel preview = label(" ", font(Font.BOLD, 13f), PRIMARY_DARK);
        Map<DayOfWeek, DayChip> chips = new EnumMap<>(DayOfWeek.class);
        Runnable[] refresh = {() -> { }};
        Stepper hour = new Stepper(t.getHour(), 0, 23, () -> refresh[0].run());
        Stepper minute = new Stepper(t.getMinute(), 0, 59, () -> refresh[0].run());

        JTextField labelField = new JTextField(existing != null ? existing.getLabel() : "Alarm", 22);
        labelField.setFont(font(Font.PLAIN, 15f));
        labelField.setForeground(INK);
        Border2 fieldBorder = new Border2();
        labelField.setBorder(fieldBorder);
        JLabel counter = label("", SMALL, MUTED);
        Runnable updateCounter = () -> {
            int n = labelField.getText().length();
            counter.setText(n + "/" + Alarm.MAX_LABEL_LENGTH);
            counter.setForeground(n > Alarm.MAX_LABEL_LENGTH ? ROSE : MUTED);
        };
        updateCounter.run();
        labelField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateCounter.run(); }
            public void removeUpdate(DocumentEvent e) { updateCounter.run(); }
            public void changedUpdate(DocumentEvent e) { updateCounter.run(); }
        });
        labelField.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { fieldBorder.focused = true; labelField.repaint(); }
            @Override public void focusLost(FocusEvent e) { fieldBorder.focused = false; labelField.repaint(); }
        });

        JPanel dayGrid = new JPanel(new GridLayout(1, 7, 8, 0));
        dayGrid.setOpaque(false);
        for (DayOfWeek day : DayOfWeek.values()) {
            DayChip chip = new DayChip(day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH));
            chip.setSelected(existing != null && existing.getRepeatDays().contains(day));
            chip.addItemListener(e -> refresh[0].run());
            chips.put(day, chip);
            dayGrid.add(chip);
        }

        JPanel presets = new JPanel();
        presets.setOpaque(false);
        presets.setLayout(new BoxLayout(presets, BoxLayout.X_AXIS));
        presets.add(presetButton("One time", chips, EnumSet.noneOf(DayOfWeek.class)));
        presets.add(Box.createHorizontalStrut(8));
        presets.add(presetButton("Sun - Thu", chips, EnumSet.of(DayOfWeek.SUNDAY, DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)));
        presets.add(Box.createHorizontalStrut(8));
        presets.add(presetButton("Weekend", chips, EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)));
        presets.add(Box.createHorizontalStrut(8));
        presets.add(presetButton("Every day", chips, EnumSet.allOf(DayOfWeek.class)));
        presets.add(Box.createHorizontalGlue());
        presets.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        refresh[0] = () -> {
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            chips.forEach((d, c) -> { if (c.isSelected()) days.add(d); });
            LocalDateTime when = nextOccurrence(LocalTime.of(hour.getValue(), minute.getValue()), days);
            preview.setText(when == null ? " " : "Rings in " + untilText(when)
                    + (days.isEmpty() ? "  ·  one time" : "  ·  repeats"));
        };
        refresh[0].run();

        JDialog dialog = new JDialog(this, existing == null ? "New alarm" : "Edit alarm", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(WHITE);

        JPanel top = new GradientStrip();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(new EmptyBorder(22, 30, 20, 30));
        top.add(label(existing == null ? "Create an alarm" : "Edit your alarm", font(Font.BOLD, 26f), WHITE));
        top.add(Box.createVerticalStrut(5));
        top.add(label("Choose a time and make it your own.", BODY, new Color(232, 229, 255)));
        root.add(top, BorderLayout.NORTH);

        JPanel form = column();
        form.setBorder(new EmptyBorder(22, 30, 8, 30));
        form.add(fieldLabel("ALARM TIME"));
        form.add(Box.createVerticalStrut(10));
        JPanel timeLine = new JPanel();
        timeLine.setOpaque(false);
        timeLine.setLayout(new BoxLayout(timeLine, BoxLayout.X_AXIS));
        timeLine.add(hour);
        timeLine.add(Box.createHorizontalStrut(12));
        timeLine.add(label(":", font(Font.BOLD, 38f), MUTED));
        timeLine.add(Box.createHorizontalStrut(12));
        timeLine.add(minute);
        timeLine.add(Box.createHorizontalStrut(20));
        timeLine.add(preview);
        timeLine.add(Box.createHorizontalGlue());
        timeLine.setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
        form.add(timeLine);
        form.add(Box.createVerticalStrut(18));
        JPanel nameHead = new JPanel(new BorderLayout());
        nameHead.setOpaque(false);
        nameHead.add(fieldLabel("ALARM NAME"), BorderLayout.WEST);
        nameHead.add(counter, BorderLayout.EAST);
        nameHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        form.add(nameHead);
        form.add(Box.createVerticalStrut(9));
        labelField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        form.add(labelField);
        form.add(Box.createVerticalStrut(20));
        form.add(fieldLabel("REPEAT DAYS"));
        form.add(Box.createVerticalStrut(11));
        dayGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        form.add(dayGrid);
        form.add(Box.createVerticalStrut(11));
        form.add(presets);
        form.add(Box.createVerticalStrut(8));
        form.add(label("No days selected = one-time alarm", SMALL, MUTED));
        form.add(Box.createVerticalGlue());
        for (Component c : form.getComponents()) {
            if (c instanceof JComponent) ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        root.add(form, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 11, 0));
        buttons.setOpaque(false);
        buttons.setBorder(new EmptyBorder(14, 30, 26, 30));
        StyleButton cancel = new StyleButton("Cancel", PRIMARY_PALE, PRIMARY_DARK, new Color(225, 219, 255), 14, 20, false);
        StyleButton save = new StyleButton(existing == null ? "Create alarm" : "Save changes",
                PRIMARY, WHITE, PRIMARY_DARK, 14, 20, false);
        final AlarmInput[] result = {null};
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            Set<DayOfWeek> chosen = EnumSet.noneOf(DayOfWeek.class);
            chips.forEach((d, c) -> { if (c.isSelected()) chosen.add(d); });
            result[0] = new AlarmInput(LocalTime.of(hour.getValue(), minute.getValue()), labelField.getText(), chosen);
            dialog.dispose();
        });
        buttons.add(cancel);
        buttons.add(save);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(540, 600);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        dialog.getRootPane().setDefaultButton(null);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setVisible(true);
        return result[0];
    }

    private StyleButton presetButton(String text, Map<DayOfWeek, DayChip> chips, Set<DayOfWeek> days) {
        StyleButton b = new StyleButton(text, STRIPE, MUTED, PRIMARY_PALE, 7, 13, false);
        b.setFont(font(Font.BOLD, 12f));
        b.addActionListener(e -> chips.forEach((d, c) -> c.setSelected(days.contains(d))));
        return b;
    }

    // ================================================================= helpers
    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        return l;
    }

    private static JLabel fieldLabel(String text) { return label(text, font(Font.BOLD, 12f), MUTED); }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static Color lerp(Color a, Color b, float t) {
        t = clamp(t);
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    private static Color alpha(Color c, float a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.round(255 * clamp(a))); }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    private static Graphics2D smooth(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    private static void centered(Graphics2D g, String text, Font f, Color c, float cx, float baseline) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(text, cx - g.getFontMetrics().stringWidth(text) / 2f, baseline);
    }

    private static String fit(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) return s;
        String ell = "...";
        while (s.length() > 1 && fm.stringWidth(s + ell) > width) s = s.substring(0, s.length() - 1);
        return s + ell;
    }

    private static Image appIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = smooth(img.getGraphics());
        g.setPaint(new GradientPaint(0, 0, new Color(90, 76, 206), 64, 64, new Color(255, 140, 120)));
        g.fillRoundRect(2, 2, 60, 60, 20, 20);
        g.setColor(WHITE);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawOval(14, 14, 36, 36);
        g.drawLine(32, 32, 32, 22);
        g.drawLine(32, 32, 40, 36);
        g.dispose();
        return img;
    }

    private static void drawSun(Graphics2D g, float cx, float cy, float r, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke(r * 0.28f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            g.draw(new java.awt.geom.Line2D.Double(cx + Math.cos(a) * r * 1.55, cy + Math.sin(a) * r * 1.55,
                    cx + Math.cos(a) * r * 2.05, cy + Math.sin(a) * r * 2.05));
        }
    }

    private static void drawMoon(Graphics2D g, float cx, float cy, float r, Color c) {
        Area moon = new Area(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
        moon.subtract(new Area(new Ellipse2D.Float(cx - r * 0.35f, cy - r * 1.15f, 2 * r, 2 * r)));
        g.setColor(c);
        g.fill(moon);
    }

    // ================================================================ animation
    /** A value that glides toward a target (easing); repaints its owner on each frame. */
    private static final class Smooth {
        private final JComponent owner;
        private final float speed;
        private float value, target;
        private Timer timer;
        Smooth(JComponent owner, float initial, float speed) { this.owner = owner; this.value = this.target = initial; this.speed = speed; }
        float get() { return value; }
        void set(float v) { value = target = v; }
        void to(float t) {
            target = t;
            if (timer == null) timer = new Timer(16, e -> step());
            if (!timer.isRunning()) timer.start();
        }
        private void step() {
            value += (target - value) * speed;
            if (Math.abs(target - value) < 0.004f) { value = target; timer.stop(); }
            owner.repaint();
        }
    }

    // ================================================================ hero panel
    /** Left side: living sky, orbiting sun/moon, stars, analog clock with a smooth second hand. */
    private final class Hero extends JComponent {
        private final float[][] stars = new float[70][4];
        private final float[][] bubbles = new float[7][5];
        private final Timer timer = new Timer(16, e -> { if (isShowing()) repaint(); });
        private final long t0 = System.nanoTime();

        Hero() {
            setPreferredSize(new Dimension(392, 600));
            setOpaque(false);
            Random r = new Random(11);
            for (float[] s : stars) { s[0] = r.nextFloat(); s[1] = r.nextFloat() * 0.62f; s[2] = 0.8f + r.nextFloat() * 1.8f; s[3] = r.nextFloat() * 6.28f; }
            for (float[] b : bubbles) { b[0] = r.nextFloat(); b[1] = r.nextFloat(); b[2] = 18 + r.nextFloat() * 46; b[3] = 6 + r.nextFloat() * 10; b[4] = r.nextFloat() * 6.28f; }
            timer.start();
        }

        private double secondsOfDay() {
            double extra = Math.min(1.5, (System.nanoTime() - lastNanos) / 1e9);
            return lastNow.toNanoOfDay() / 1e9 + extra;
        }

        private Color[] sky(double h) {
            double[] hs = {0, 5, 7, 11, 16, 18.5, 21, 24};
            Color night1 = new Color(11, 14, 46), night2 = new Color(42, 38, 112);
            Color dawn1 = new Color(88, 70, 152), dawn2 = new Color(240, 132, 142);
            Color day1 = new Color(47, 112, 212), day2 = new Color(122, 192, 246);
            Color dusk1 = new Color(60, 42, 128), dusk2 = new Color(232, 112, 98);
            Color[][] cs = {{night1, night2}, {night1, night2}, {dawn1, dawn2}, {day1, day2},
                    {day1, day2}, {dusk1, dusk2}, {night1, night2}, {night1, night2}};
            for (int i = 0; i < hs.length - 1; i++) {
                if (h >= hs[i] && h <= hs[i + 1]) {
                    float t = (float) ((h - hs[i]) / (hs[i + 1] - hs[i]));
                    return new Color[]{lerp(cs[i][0], cs[i + 1][0], t), lerp(cs[i][1], cs[i + 1][1], t)};
                }
            }
            return cs[0];
        }

        private float nightFactor(double h) {
            if (h < 5) return 1f;
            if (h < 7) return (float) (1 - (h - 5) / 2);
            if (h < 18.5) return 0f;
            if (h < 20.5) return (float) ((h - 18.5) / 2);
            return 1f;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            int w = getWidth(), h = getHeight();
            double sod = secondsOfDay();
            double hours = (sod / 3600.0) % 24;
            double t = (System.nanoTime() - t0) / 1e9;

            g.setClip(new RoundRectangle2D.Float(0, 0, w, h, 34, 34));
            Color[] sky = sky(hours);
            g.setPaint(new GradientPaint(0, 0, sky[0], 0, h, sky[1]));
            g.fillRect(0, 0, w, h);

            // stars
            float nf = nightFactor(hours);
            if (nf > 0.01f) {
                for (float[] s : stars) {
                    float tw = (float) (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * 1.6 + s[3])));
                    g.setColor(new Color(255, 255, 255, Math.round(230 * nf * tw)));
                    g.fill(new Ellipse2D.Float(s[0] * w, s[1] * h, s[2], s[2]));
                }
            }

            // drifting soft bubbles
            for (float[] b : bubbles) {
                float span = h + 120;
                float y = ((b[1] * span - (float) t * b[3]) % span + span) % span - 60;
                float x = b[0] * w + (float) Math.sin(t * 0.5 + b[4]) * 16;
                g.setColor(new Color(255, 255, 255, 11));
                g.fill(new Ellipse2D.Float(x - b[2] / 2, y - b[2] / 2, b[2], b[2]));
            }

            // sun / moon travelling over the top
            boolean day = hours >= 6 && hours < 18.5;
            double p = day ? (hours - 6) / 12.5 : ((hours - 18.5 + 24) % 24) / 11.5;
            float ox = (float) (w * (0.14 + 0.72 * p));
            float oy = (float) (196 - Math.sin(Math.PI * p) * 64);
            float orbR = 24;
            Color glow = day ? new Color(255, 214, 120) : new Color(214, 220, 255);
            g.setPaint(new RadialGradientPaint(ox, oy, 70, new float[]{0f, 1f}, new Color[]{alpha(glow, 0.45f), alpha(glow, 0f)}));
            g.fill(new Ellipse2D.Float(ox - 70, oy - 70, 140, 140));
            if (day) drawSun(g, ox, oy, 15, new Color(255, 224, 140));
            else drawMoon(g, ox, oy, orbR * 0.8f, new Color(244, 244, 255));

            // greeting + title
            String greet = hours < 5 ? "GOOD NIGHT" : hours < 12 ? "GOOD MORNING" : hours < 17 ? "GOOD AFTERNOON" : hours < 21 ? "GOOD EVENING" : "GOOD NIGHT";
            g.setFont(font(Font.BOLD, 12f));
            g.setColor(new Color(255, 255, 255, 190));
            g.drawString(greet, 32, 46);
            g.setFont(font(Font.BOLD, 30f));
            g.setColor(WHITE);
            g.drawString("Alarm Clock", 32, 80);

            // analog clock
            int bottomReserve = 196;
            float cx = w / 2f;
            float R = Math.max(60, Math.min(118, (h - 215 - bottomReserve) / 2f - 6));
            float cy = 215 + (h - 215 - bottomReserve) / 2f;
            g.setColor(new Color(255, 255, 255, 30));
            g.fill(new Ellipse2D.Float(cx - R - 12, cy - R - 12, 2 * R + 24, 2 * R + 24));
            g.setPaint(new RadialGradientPaint(cx, cy, R, new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 46), new Color(255, 255, 255, 22)}));
            g.fill(new Ellipse2D.Float(cx - R, cy - R, 2 * R, 2 * R));
            g.setColor(new Color(255, 255, 255, 110));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Float(cx - R, cy - R, 2 * R, 2 * R));

            for (int i = 0; i < 60; i++) {
                double a = Math.toRadians(i * 6 - 90);
                boolean major = i % 5 == 0;
                float r1 = R - 5, r2 = R - (major ? 15 : 9);
                g.setStroke(new BasicStroke(major ? 2.6f : 1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(255, 255, 255, major ? 230 : 120));
                g.draw(new java.awt.geom.Line2D.Double(cx + Math.cos(a) * r1, cy + Math.sin(a) * r1,
                        cx + Math.cos(a) * r2, cy + Math.sin(a) * r2));
            }
            Font numFont = font(Font.BOLD, 15f);
            String[] nums = {"12", "3", "6", "9"};
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90 - 90);
                g.setFont(numFont);
                FontMetrics fm = g.getFontMetrics();
                float nx = (float) (cx + Math.cos(a) * (R - 32));
                float ny = (float) (cy + Math.sin(a) * (R - 32));
                g.setColor(new Color(255, 255, 255, 225));
                g.drawString(nums[i], nx - fm.stringWidth(nums[i]) / 2f, ny + (fm.getAscent() - fm.getDescent()) / 2f);
            }

            // alarm markers on the dial (enabled alarms), the next one pulses
            for (Alarm a : shown) {
                if (!a.isEnabled()) continue;
                double frac = ((a.getTime().getHour() % 12) + a.getTime().getMinute() / 60.0) / 12.0;
                double ang = Math.toRadians(frac * 360 - 90);
                float mx = (float) (cx + Math.cos(ang) * (R + 0.5f)), my = (float) (cy + Math.sin(ang) * (R + 0.5f));
                boolean isNext = a.getId() == nextId;
                if (isNext) {
                    float pulse = (float) ((t * 0.9) % 1.0);
                    g.setColor(new Color(255, 176, 64, Math.round(150 * (1 - pulse))));
                    float pr = 6 + pulse * 16;
                    g.fill(new Ellipse2D.Float(mx - pr, my - pr, 2 * pr, 2 * pr));
                }
                g.setColor(isNext ? AMBER : new Color(255, 255, 255, 235));
                g.fill(new Ellipse2D.Float(mx - 5.5f, my - 5.5f, 11, 11));
                g.setColor(new Color(40, 30, 100, 140));
                g.setStroke(new BasicStroke(1.4f));
                g.draw(new Ellipse2D.Float(mx - 5.5f, my - 5.5f, 11, 11));
            }

            // hands
            double hourAng = ((sod / 3600.0) % 12) / 12.0 * 360;
            double minAng = ((sod / 60.0) % 60) / 60.0 * 360;
            double secAng = (sod % 60) / 60.0 * 360;
            hand(g, cx, cy, hourAng, R * 0.48f, 6.5f, WHITE, 0);
            hand(g, cx, cy, minAng, R * 0.72f, 4.2f, WHITE, 0);
            hand(g, cx, cy, secAng, R * 0.84f, 2.2f, AMBER, 20);
            g.setColor(WHITE);
            g.fill(new Ellipse2D.Float(cx - 7, cy - 7, 14, 14));
            g.setColor(AMBER);
            g.fill(new Ellipse2D.Float(cx - 3.5f, cy - 3.5f, 7, 7));

            // digital time and date
            long s = (long) Math.floor(sod) % 86400;
            String hm = String.format("%02d:%02d", s / 3600, (s % 3600) / 60);
            String ss = String.format(":%02d", s % 60);
            Font big = font(Font.BOLD, 54f), small = font(Font.BOLD, 26f);
            g.setFont(big);
            int wBig = g.getFontMetrics().stringWidth(hm);
            g.setFont(small);
            int wSmall = g.getFontMetrics().stringWidth(ss);
            float startX = cx - (wBig + wSmall) / 2f;
            float baseY = h - 128;
            g.setFont(big);
            g.setColor(WHITE);
            g.drawString(hm, startX, baseY);
            g.setFont(small);
            g.setColor(new Color(255, 255, 255, 190));
            g.drawString(ss, startX + wBig + 2, baseY);
            centered(g, lastDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)),
                    font(Font.PLAIN, 15f), new Color(255, 255, 255, 215), cx, baseY + 28);

            // next alarm pill
            float px = 24, pw = w - 48, ph = 56, py = h - ph - 26;
            g.setColor(new Color(255, 255, 255, 40));
            g.fill(new RoundRectangle2D.Float(px, py, pw, ph, 22, 22));
            g.setColor(new Color(255, 255, 255, 70));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Float(px + .5f, py + .5f, pw - 1, ph - 1, 22, 22));
            float dotX = px + 24, dotY = py + ph / 2;
            if (nextLabel != null) {
                float pulse = (float) ((t * 0.9) % 1.0);
                g.setColor(new Color(255, 176, 64, Math.round(140 * (1 - pulse))));
                float pr = 5 + pulse * 10;
                g.fill(new Ellipse2D.Float(dotX - pr, dotY - pr, 2 * pr, 2 * pr));
                g.setColor(AMBER);
            } else g.setColor(new Color(255, 255, 255, 120));
            g.fill(new Ellipse2D.Float(dotX - 5, dotY - 5, 10, 10));
            g.setFont(font(Font.BOLD, 11f));
            g.setColor(new Color(255, 255, 255, 185));
            g.drawString(nextLabel != null ? "NEXT ALARM" : "ALL QUIET", dotX + 18, py + 22);
            g.setFont(font(Font.BOLD, 15f));
            g.setColor(WHITE);
            String line = nextLabel != null ? nextLabel + "  ·  in " + nextIn : "No alarms are set";
            g.drawString(fit(line, g.getFontMetrics(), (int) (pw - 64)), dotX + 18, py + 42);
            g.dispose();
        }

        private void hand(Graphics2D g, float cx, float cy, double deg, float len, float width, Color c, float tail) {
            double a = Math.toRadians(deg - 90);
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0, 0, 0, 40));
            g.draw(new java.awt.geom.Line2D.Double(cx - Math.cos(a) * tail + 1.5, cy - Math.sin(a) * tail + 2.5,
                    cx + Math.cos(a) * len + 1.5, cy + Math.sin(a) * len + 2.5));
            g.setColor(c);
            g.draw(new java.awt.geom.Line2D.Double(cx - Math.cos(a) * tail, cy - Math.sin(a) * tail,
                    cx + Math.cos(a) * len, cy + Math.sin(a) * len));
        }
    }

    // =============================================================== count badge
    private final class Badge extends JComponent {
        private int count = 0;
        private final Smooth pop = new Smooth(this, 0f, 0.2f);
        Badge() { setPreferredSize(new Dimension(112, 36)); setOpaque(false); }
        void setCount(int n) {
            if (n != count) { count = n; pop.set(1f); pop.to(0f); }
            repaint();
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            float s = 1f + 0.18f * pop.get();
            g.translate(getWidth() / 2f, getHeight() / 2f);
            g.scale(s, s);
            g.translate(-getWidth() / 2f, -getHeight() / 2f);
            g.setColor(lerp(PRIMARY_PALE, new Color(224, 218, 255), pop.get()));
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18));
            centered(g, count + (count == 1 ? " ALARM" : " ALARMS"), font(Font.BOLD, 12f), PRIMARY_DARK,
                    getWidth() / 2f, getHeight() / 2f + 4.5f);
            g.dispose();
        }
    }

    // ============================================================== alarm cards
    /** Custom-drawn animated list of alarm cards (slide-in, glide on delete, hover lift, switch). */
    private final class AlarmList extends JComponent implements Scrollable {
        private static final int CARD_H = 88, GAP = 12, PAD = 8;
        private final List<Card> cards = new ArrayList<>();
        private final Timer timer = new Timer(16, e -> step());
        private final long t0 = System.nanoTime();
        Integer selectedId, hoverId;

        private final class Card {
            Alarm alarm;
            float y, targetY, enter, delay, hover, sel, sw, fade = 1f;
            boolean leaving;
            Card(Alarm a, float y) { this.alarm = a; this.y = this.targetY = y; this.sw = a.isEnabled() ? 1f : 0f; }
        }

        AlarmList() {
            setOpaque(false);
            setFocusable(true);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    Card c = cardAt(e.getPoint());
                    Integer id = c == null ? null : c.alarm.getId();
                    boolean onSwitch = c != null && switchRect(c).contains(e.getPoint());
                    setCursor(Cursor.getPredefinedCursor(c == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                    if (onSwitch) setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    if (!java.util.Objects.equals(id, hoverId)) { hoverId = id; timer.start(); }
                }
                @Override public void mouseExited(MouseEvent e) { hoverId = null; timer.start(); }
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    Card c = cardAt(e.getPoint());
                    if (c == null) { selectedId = null; timer.start(); return; }
                    select(c);
                    if (switchRect(c).contains(e.getPoint())) toggleAction.run();
                    else if (e.getClickCount() == 2) editAction.run();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            bind(KeyEvent.VK_UP, () -> moveSelection(-1));
            bind(KeyEvent.VK_DOWN, () -> moveSelection(1));
            bind(KeyEvent.VK_ENTER, () -> { if (selectedId != null) editAction.run(); });
            bind(KeyEvent.VK_DELETE, () -> { if (selectedId != null) deleteAction.run(); });
        }

        private void bind(int key, Runnable r) {
            String name = "k" + key;
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
            getActionMap().put(name, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } });
        }

        private List<Card> live() {
            List<Card> l = new ArrayList<>();
            for (Card c : cards) if (!c.leaving) l.add(c);
            return l;
        }

        private void moveSelection(int d) {
            List<Card> l = live();
            if (l.isEmpty()) return;
            int idx = -1;
            for (int i = 0; i < l.size(); i++) if (selectedId != null && l.get(i).alarm.getId() == selectedId) idx = i;
            idx = Math.max(0, Math.min(l.size() - 1, idx + d));
            select(l.get(idx));
        }

        private void select(Card c) {
            selectedId = c.alarm.getId();
            scrollRectToVisible(new Rectangle(0, (int) c.targetY - 6, getWidth(), CARD_H + 12));
            timer.start();
        }

        private Card cardAt(Point p) {
            for (Card c : live()) {
                if (new Rectangle(PAD, (int) c.y, getWidth() - 2 * PAD, CARD_H).contains(p)) return c;
            }
            return null;
        }

        private Rectangle switchRect(Card c) { return new Rectangle(getWidth() - PAD - 22 - 54, (int) c.y + (CARD_H - 32) / 2, 54, 32); }

        void setAlarms(List<Alarm> alarms) {
            List<Alarm> sorted = new ArrayList<>(alarms);
            sorted.sort(Comparator.comparing(Alarm::getTime).thenComparingInt(Alarm::getId));
            Map<Integer, Card> liveMap = new HashMap<>();
            for (Card c : cards) if (!c.leaving) liveMap.put(c.alarm.getId(), c);
            boolean first = liveMap.isEmpty();
            List<Card> ordered = new ArrayList<>();
            int idx = 0;
            for (Alarm a : sorted) {
                Card c = liveMap.remove(a.getId());
                if (c == null) {
                    c = new Card(a, PAD + idx * (CARD_H + GAP));
                    c.delay = first ? idx * 5 : 0;
                    if (!first || sorted.size() == 1) selectedId = a.getId();   // highlight the new card
                } else c.alarm = a;
                c.targetY = PAD + idx * (CARD_H + GAP);
                ordered.add(c);
                idx++;
            }
            for (Card gone : liveMap.values()) gone.leaving = true;
            List<Card> next = new ArrayList<>(ordered);
            for (Card c : cards) if (c.leaving) next.add(c);
            cards.clear();
            cards.addAll(next);
            if (selectedId != null && sorted.stream().noneMatch(a -> a.getId() == selectedId)) selectedId = null;
            revalidate();
            repaint();
            timer.start();
        }

        private float approach(float v, float t, float k) {
            v += (t - v) * k;
            return Math.abs(t - v) < 0.01f ? t : v;
        }

        private void step() {
            boolean busy = live().isEmpty();
            boolean removed = false;
            for (Iterator<Card> it = cards.iterator(); it.hasNext(); ) {
                Card c = it.next();
                if (c.leaving) {
                    c.fade -= 0.07f;
                    if (c.fade <= 0) { it.remove(); removed = true; continue; }
                    busy = true;
                    continue;
                }
                if (c.delay > 0) { c.delay--; busy = true; }
                else if (c.enter < 1f) { c.enter = Math.min(1f, c.enter + 1f / 32f); busy = true; }
                boolean isHover = hoverId != null && hoverId == c.alarm.getId();
                boolean isSel = selectedId != null && selectedId == c.alarm.getId();
                float th = isHover ? 1f : 0f, ts = isSel ? 1f : 0f, tw = c.alarm.isEnabled() ? 1f : 0f;
                c.hover = approach(c.hover, th, 0.22f);
                c.sel = approach(c.sel, ts, 0.22f);
                c.sw = approach(c.sw, tw, 0.2f);
                c.y += (c.targetY - c.y) * 0.22f;
                if (Math.abs(c.targetY - c.y) < 0.4f) c.y = c.targetY; else busy = true;
                if (c.hover != th || c.sel != ts || c.sw != tw) busy = true;
            }
            if (removed) revalidate();
            repaint();
            if (!busy) timer.stop();
        }

        @Override public Dimension getPreferredSize() {
            int n = live().size();
            return new Dimension(300, PAD * 2 + Math.max(0, n) * (CARD_H + GAP));
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            if (live().isEmpty() && cards.isEmpty()) paintEmpty(g);
            for (Card c : cards) {
                float e = c.leaving ? 1f : easeOutBack(clamp(c.enter));
                float a = c.leaving ? c.fade : clamp(c.enter * 2.4f);
                if (a <= 0.01f) continue;
                float dx = c.leaving ? (1f - c.fade) * 90f : (1f - e) * 80f;
                float dy = -3f * c.hover;
                Graphics2D cg = (Graphics2D) g.create();
                cg.translate(dx, dy);
                paintCard(cg, c, a);
                cg.dispose();
            }
            g.dispose();
        }

        private void paintCard(Graphics2D g, Card c, float a) {
            int x = PAD, w = getWidth() - 2 * PAD, y = Math.round(c.y), R = 22;
            Alarm al = c.alarm;
            // soft shadow
            for (int i = 0; i < 7; i++) {
                float k = (1f - i / 7f);
                g.setColor(new Color(60, 50, 140, Math.round(a * 9 * k * (1f + c.hover * 0.9f + c.sel * 0.4f))));
                g.fill(new RoundRectangle2D.Float(x - i, y - i + 4, w + 2 * i, CARD_H + 2 * i, R + i, R + i));
            }
            g.setComposite(AlphaComposite.SrcOver.derive(a));
            g.setColor(lerp(WHITE, new Color(244, 241, 255), c.sel));
            g.fill(new RoundRectangle2D.Float(x, y, w, CARD_H, R, R));
            if (c.sel > 0.01f) {
                g.setColor(alpha(PRIMARY, c.sel));
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Float(x + 1, y + 1, w - 2, CARD_H - 2, R, R));
            }
            // accent bar
            g.setColor(lerp(new Color(214, 218, 232), PRIMARY, c.sw));
            g.fill(new RoundRectangle2D.Float(x + 10, y + 22, 4, CARD_H - 44, 4, 4));

            float content = a * (0.55f + 0.45f * c.sw);
            g.setComposite(AlphaComposite.SrcOver.derive(clamp(content)));
            boolean daytime = al.getTime().getHour() >= 6 && al.getTime().getHour() < 18;
            float ix = x + 36, iy = y + CARD_H / 2f;
            g.setColor(daytime ? new Color(255, 242, 214) : new Color(232, 229, 255));
            g.fill(new Ellipse2D.Float(ix - 23, iy - 23, 46, 46));
            if (daytime) drawSun(g, ix, iy, 7f, new Color(247, 170, 40));
            else drawMoon(g, ix, iy, 11f, new Color(98, 84, 224));

            g.setFont(font(Font.BOLD, 30f));
            g.setColor(INK);
            g.drawString(al.getTime().format(DateTimeFormatter.ofPattern("HH:mm")), x + 78, y + 44);

            int switchX = getWidth() - PAD - 22 - 54;
            int daysX = switchX - 26 - 178;
            g.setFont(font(Font.PLAIN, 14f));
            g.setColor(MUTED);
            g.drawString(fit(al.getLabel(), g.getFontMetrics(), daysX - (x + 78) - 14), x + 78, y + 68);

            // repeat days
            String[] letters = {"M", "T", "W", "T", "F", "S", "S"};
            if (al.getRepeatDays().isEmpty()) {
                g.setFont(font(Font.BOLD, 12f));
                int pw = g.getFontMetrics().stringWidth("ONE TIME") + 26;
                float px = switchX - 26 - pw, py = y + (CARD_H - 28) / 2f;
                g.setColor(PRIMARY_PALE);
                g.fill(new RoundRectangle2D.Float(px, py, pw, 28, 14, 14));
                centered(g, "ONE TIME", font(Font.BOLD, 12f), PRIMARY_DARK, px + pw / 2f, py + 18);
            } else {
                for (int i = 0; i < 7; i++) {
                    boolean on = al.getRepeatDays().contains(DayOfWeek.of(i + 1));
                    float dx = daysX + i * 26, dy = y + (CARD_H - 22) / 2f;
                    g.setColor(on ? PRIMARY : STRIPE);
                    g.fill(new Ellipse2D.Float(dx, dy, 22, 22));
                    centered(g, letters[i], font(Font.BOLD, 11f), on ? WHITE : new Color(160, 165, 188), dx + 11, dy + 15);
                }
            }

            // switch (drawn at full strength so it is always readable)
            g.setComposite(AlphaComposite.SrcOver.derive(a));
            Rectangle sw = new Rectangle(switchX, y + (CARD_H - 32) / 2, 54, 32);
            g.setColor(lerp(new Color(208, 212, 228), MINT, c.sw));
            g.fill(new RoundRectangle2D.Float(sw.x, sw.y, sw.width, sw.height, 32, 32));
            float kx = sw.x + 3 + c.sw * (sw.width - 32 + 0);
            g.setColor(new Color(0, 0, 0, 40));
            g.fill(new Ellipse2D.Float(kx + 1, sw.y + 5, 26, 26));
            g.setColor(WHITE);
            g.fill(new Ellipse2D.Float(kx, sw.y + 3, 26, 26));
            g.setFont(font(Font.BOLD, 9f));
            if (c.sw > 0.5f) { g.setColor(alpha(WHITE, (c.sw - 0.5f) * 2)); g.drawString("ON", sw.x + 9, sw.y + 20); }
            else { g.setColor(alpha(new Color(120, 126, 150), (0.5f - c.sw) * 2)); g.drawString("OFF", sw.x + 31, sw.y + 20); }
        }

        private void paintEmpty(Graphics2D g) {
            double t = (System.nanoTime() - t0) / 1e9;
            float cx = getWidth() / 2f;
            float cy = Math.max(120, getHeight() / 2f - 30);
            float bob = (float) Math.sin(t * 2.0) * 7f;
            g.setColor(new Color(107, 94, 237, 22));
            g.fill(new Ellipse2D.Float(cx - 64, cy - 64 + bob, 128, 128));
            g.setColor(PRIMARY_PALE);
            g.fill(new Ellipse2D.Float(cx - 46, cy - 46 + bob, 92, 92));
            g.setColor(PRIMARY);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Ellipse2D.Float(cx - 22, cy - 22 + bob, 44, 44));
            g.draw(new java.awt.geom.Line2D.Float(cx, cy + bob, cx, cy - 12 + bob));
            g.draw(new java.awt.geom.Line2D.Float(cx, cy + bob, cx + 10, cy + 6 + bob));
            for (int i = 0; i < 3; i++) {
                float ph = (float) ((t * 0.5 + i / 3.0) % 1.0);
                g.setColor(new Color(107, 94, 237, Math.round(190 * (1 - ph))));
                g.setFont(font(Font.BOLD, 14f + i * 4f));
                g.drawString("z", cx + 44 + ph * 26 + i * 6, cy - 28 - ph * 40 + bob);
            }
            centered(g, "No alarms yet", font(Font.BOLD, 19f), INK, cx, cy + 98);
            centered(g, "Press  + Add alarm  to create your first reminder", BODY, MUTED, cx, cy + 124);
        }

        // Scrollable
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 24; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height - 40; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
        }
    }

    private static final class SlimScrollUI extends BasicScrollBarUI {
        @Override protected void configureScrollBarColors() { thumbColor = new Color(205, 202, 232); trackColor = BACKGROUND; }
        private JButton none() { JButton b = new JButton(); b.setPreferredSize(new Dimension(0, 0)); return b; }
        @Override protected JButton createDecreaseButton(int o) { return none(); }
        @Override protected JButton createIncreaseButton(int o) { return none(); }
        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }
        @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = smooth(g);
            g2.setColor(thumbColor);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g2.dispose();
        }
        @Override public Dimension getPreferredSize(JComponent c) { return new Dimension(10, 10); }
    }

    // ============================================================= ringing panel
    private final class RingPanel extends JPanel {
        private final Alarm alarm;
        private final Timer timer = new Timer(16, e -> repaint());
        private final long t0 = System.nanoTime();
        RingPanel(Alarm alarm) { this.alarm = alarm; setOpaque(true); }
        void start() { timer.start(); }
        void stop() { timer.stop(); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            int w = getWidth(), h = getHeight();
            double t = (System.nanoTime() - t0) / 1e9;
            g.setPaint(new GradientPaint(0, 0, new Color(52, 36, 128), 0, h, new Color(226, 98, 112)));
            g.fillRect(0, 0, w, h);
            float cx = w / 2f, cy = 128;
            // expanding rings
            for (int i = 0; i < 4; i++) {
                float p = (float) ((t * 0.7 + i / 4.0) % 1.0);
                float r = 34 + p * 130;
                g.setColor(new Color(255, 255, 255, Math.round(70 * (1 - p))));
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
            }
            g.setPaint(new RadialGradientPaint(cx, cy, 90, new float[]{0f, 1f},
                    new Color[]{new Color(255, 255, 255, 60), new Color(255, 255, 255, 0)}));
            g.fill(new Ellipse2D.Float(cx - 90, cy - 90, 180, 180));

            // shaking bell
            double envelope = Math.pow(Math.max(0, Math.sin(t * 3.4)), 2);
            double angle = Math.sin(t * 24) * 0.30 * envelope;
            Graphics2D b = (Graphics2D) g.create();
            b.translate(cx, cy - 6);
            b.rotate(angle, 0, -46);
            Path2D bell = new Path2D.Float();
            bell.moveTo(-36, 22);
            bell.curveTo(-34, 20, -28, 8, -28, -10);
            bell.curveTo(-28, -34, -14, -42, 0, -42);
            bell.curveTo(14, -42, 28, -34, 28, -10);
            bell.curveTo(28, 8, 34, 20, 36, 22);
            bell.closePath();
            b.setColor(new Color(0, 0, 0, 40));
            b.translate(2, 4);
            b.fill(bell);
            b.translate(-2, -4);
            b.setColor(WHITE);
            b.fill(bell);
            b.fill(new RoundRectangle2D.Float(-42, 20, 84, 10, 10, 10));
            b.fill(new Ellipse2D.Float(-5.5f, -52, 11, 11));
            double clap = Math.sin(t * 24 + 1.2) * 6 * envelope;
            b.setColor(AMBER);
            b.fill(new Ellipse2D.Float((float) (-7 + clap), 27, 14, 14));
            b.dispose();

            centered(g, "TIME TO WAKE UP", font(Font.BOLD, 12f), new Color(255, 255, 255, 200), cx, 240);
            g.setFont(font(Font.BOLD, 28f));
            String name = fit(alarm.getLabel(), g.getFontMetrics(), w - 70);
            centered(g, name, font(Font.BOLD, 28f), WHITE, cx, 284);
            centered(g, alarm.getTime().format(DateTimeFormatter.ofPattern("HH:mm")), font(Font.BOLD, 54f), WHITE, cx, 350);
            g.dispose();
        }
    }

    // ============================================================ small widgets
    private static final class GradientStrip extends JPanel {
        GradientStrip() { setOpaque(true); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            g.setPaint(new GradientPaint(0, 0, new Color(86, 72, 208), getWidth(), getHeight(), new Color(150, 124, 246)));
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(new Color(255, 255, 255, 22));
            g.fillOval(getWidth() - 150, -90, 230, 230);
            g.setColor(new Color(255, 255, 255, 16));
            g.fillOval(getWidth() - 260, 30, 150, 150);
            g.dispose();
        }
    }

    private static final class Border2 extends javax.swing.border.AbstractBorder {
        boolean focused;
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = smooth(g);
            g2.setColor(focused ? PRIMARY : BORDER);
            g2.setStroke(new BasicStroke(focused ? 2f : 1.2f));
            g2.draw(new RoundRectangle2D.Float(x + 1, y + 1, w - 3, h - 3, 14, 14));
            g2.dispose();
        }
        @Override public Insets getBorderInsets(Component c) { return new Insets(13, 15, 13, 15); }
    }

    /** Big touch-friendly number stepper (click the top/bottom, scroll the wheel, or use arrow keys). */
    private static final class Stepper extends JComponent {
        private final int min, max;
        private int value;
        private final Runnable onChange;
        private final Smooth hoverUp = new Smooth(this, 0f, 0.25f);
        private final Smooth hoverDown = new Smooth(this, 0f, 0.25f);
        private final Smooth pop = new Smooth(this, 0f, 0.22f);

        Stepper(int initial, int min, int max, Runnable onChange) {
            this.min = min; this.max = max; this.value = initial; this.onChange = onChange;
            setPreferredSize(new Dimension(122, 108));
            setFocusable(true);
            MouseAdapter m = new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    hoverUp.to(e.getY() < getHeight() * 0.32 ? 1f : 0f);
                    hoverDown.to(e.getY() > getHeight() * 0.68 ? 1f : 0f);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }
                @Override public void mouseExited(MouseEvent e) { hoverUp.to(0f); hoverDown.to(0f); }
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (e.getY() < getHeight() * 0.32) step(1);
                    else if (e.getY() > getHeight() * 0.68) step(-1);
                }
                @Override public void mouseWheelMoved(MouseWheelEvent e) { step(-e.getWheelRotation()); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
            addMouseWheelListener(m);
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "up");
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "down");
            getActionMap().put("up", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(1); } });
            getActionMap().put("down", new AbstractAction() { public void actionPerformed(ActionEvent e) { step(-1); } });
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { repaint(); }
                @Override public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        int getValue() { return value; }

        private void step(int d) {
            int n = max - min + 1;
            value = ((value - min + d) % n + n) % n + min;
            pop.set(1f);
            pop.to(0f);
            repaint();
            onChange.run();
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            int w = getWidth(), h = getHeight();
            g.setColor(isFocusOwner() ? new Color(244, 241, 255) : STRIPE);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, 22, 22));
            g.setColor(isFocusOwner() ? PRIMARY : BORDER);
            g.setStroke(new BasicStroke(isFocusOwner() ? 2f : 1.2f));
            g.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 22, 22));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(lerp(new Color(185, 188, 212), PRIMARY, hoverUp.get()));
            float uy = 18 - hoverUp.get() * 3;
            g.draw(new java.awt.geom.Line2D.Float(w / 2f - 8, uy + 6, w / 2f, uy));
            g.draw(new java.awt.geom.Line2D.Float(w / 2f, uy, w / 2f + 8, uy + 6));
            g.setColor(lerp(new Color(185, 188, 212), PRIMARY, hoverDown.get()));
            float dy = h - 18 + hoverDown.get() * 3;
            g.draw(new java.awt.geom.Line2D.Float(w / 2f - 8, dy - 6, w / 2f, dy));
            g.draw(new java.awt.geom.Line2D.Float(w / 2f, dy, w / 2f + 8, dy - 6));
            float s = 1f + 0.16f * pop.get();
            g.translate(w / 2f, h / 2f);
            g.scale(s, s);
            centered(g, String.format("%02d", value), font(Font.BOLD, 44f), INK, 0, 16);
            g.dispose();
        }
    }

    /** Animated button: colour glide on hover, press squash, optional pulsing glow. */
    private static final class StyleButton extends JButton {
        private final Color fill, hoverFill;
        private final Smooth hover = new Smooth(this, 0f, 0.25f);
        private final Smooth press = new Smooth(this, 0f, 0.35f);
        private final boolean pulse;
        private final long t0 = System.nanoTime();

        StyleButton(String title, Color background, Color foreground, Color hoverColor, int vPad, int hPad, boolean pulse) {
            super(title);
            this.fill = background;
            this.hoverFill = hoverColor;
            this.pulse = pulse;
            setForeground(foreground);
            setFont(BODY_BOLD);
            setBorder(new EmptyBorder(vPad, hPad, vPad, hPad));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover.to(1f); }
                @Override public void mouseExited(MouseEvent e) { hover.to(0f); press.to(0f); }
                @Override public void mousePressed(MouseEvent e) { press.to(1f); }
                @Override public void mouseReleased(MouseEvent e) { press.to(0f); }
            });
            if (pulse) new Timer(40, e -> { if (isShowing()) repaint(); }).start();
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return pulse ? new Dimension(d.width + 20, d.height + 20) : d;
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            int inset = pulse ? 10 : 0;
            float w = getWidth() - 2 * inset, h = getHeight() - 2 * inset;
            if (pulse) {
                float p = (float) (((System.nanoTime() - t0) / 1e9 * 0.8) % 1.0);
                g.setColor(new Color(107, 94, 237, Math.round(90 * (1 - p))));
                g.setStroke(new BasicStroke(3f));
                float grow = p * 10;
                g.draw(new RoundRectangle2D.Float(inset - grow, inset - grow, w + 2 * grow, h + 2 * grow, 16 + grow, 16 + grow));
            }
            float s = 1f - 0.04f * press.get();
            g.translate(getWidth() / 2f, getHeight() / 2f);
            g.scale(s, s);
            g.translate(-getWidth() / 2f, -getHeight() / 2f);
            g.setColor(lerp(fill, hoverFill, hover.get()));
            g.fill(new RoundRectangle2D.Float(inset, inset, w, h, 16, 16));
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2f,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g.dispose();
        }
    }

    /** Day toggle whose colour fills in with a short animation. */
    private static final class DayChip extends JCheckBox {
        private final Smooth sel = new Smooth(this, 0f, 0.28f);
        private final Smooth press = new Smooth(this, 0f, 0.35f);

        DayChip(String text) {
            super(text);
            setFont(font(Font.BOLD, 12f));
            setHorizontalAlignment(CENTER);
            setFocusPainted(false);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setIcon(null);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addItemListener(e -> sel.to(isSelected() ? 1f : 0f));
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { press.to(1f); }
                @Override public void mouseReleased(MouseEvent e) { press.to(0f); }
                @Override public void mouseExited(MouseEvent e) { press.to(0f); }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = smooth(graphics);
            float s = 1f - 0.08f * press.get();
            g.translate(getWidth() / 2f, getHeight() / 2f);
            g.scale(s, s);
            g.translate(-getWidth() / 2f, -getHeight() / 2f);
            g.setColor(lerp(STRIPE, PRIMARY, sel.get()));
            g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 15, 15));
            g.setColor(lerp(new Color(150, 156, 180), WHITE, sel.get()));
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2f,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2f);
            g.dispose();
        }
    }
}
