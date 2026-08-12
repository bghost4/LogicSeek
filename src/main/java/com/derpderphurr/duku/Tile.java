package com.derpderphurr.duku;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Group;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

public class Tile extends Region {

    //Visual Elements
    public static final Color DEFAULT_COLOR = Color.LIGHTGRAY;
    private final Rectangle rect = new Rectangle();
    //overlay marks drawn on top of rect, so the tile's region color always stays visible underneath
    private final Line crossLineA = new Line();
    private final Line crossLineB = new Line();
    //wider, white copies drawn behind the marks above so they read as a light border, since
    //dark region colors can otherwise swallow a plain black/dark-green mark
    private final Line crossOutlineA = new Line();
    private final Line crossOutlineB = new Line();
    private final Group crossMark = new Group(crossOutlineA, crossOutlineB, crossLineA, crossLineB);
    private final Circle targetOutline = new Circle();
    private final Circle targetMark = new Circle();
    private Color color = DEFAULT_COLOR;

    private final Level level;

    //Logic
    private boolean target = false;
    //solved is used for if a user marks as a target and true, show the target in cell
    //if user marks as target and target is false, set the cross flag and solved flag
    private final SimpleBooleanProperty solved = new SimpleBooleanProperty(false); // works with target
    private final SimpleBooleanProperty crossed = new SimpleBooleanProperty(false); // when a user marks as not a target
    private final SimpleBooleanProperty found = new SimpleBooleanProperty(false);

    //Tile is a view/controller over its Cell: position and color group are the generator's output,
    //Tile just reads them and asks its Level to resolve colorGroup to an actual paint Color (see
    //Level.colorFor) - that indirection is what will let a future customizable stylesheet swap in
    //without Tile or Cell needing to change at all.
    private final Cell cell;

    public int getRow() {
        return cell.row;
    }

    public int getCol() {
        return cell.col;
    }

    public Tile(Level l, Cell cell) {
        this.cell = cell;
        this.level = l;

        rect.widthProperty().bindBidirectional(rect.heightProperty()); //lock in square
        // Define percentages (e.g., 20% of width for arcWidth, 40% of height for arcHeight)
        double widthPercentage = 0.20;
        double heightPercentage = 0.20;

        // Bind arc properties dynamically to dimensions
        rect.arcWidthProperty().bind(rect.widthProperty().multiply(widthPercentage));
        rect.arcHeightProperty().bind(rect.heightProperty().multiply(heightPercentage));

        rect.widthProperty().bind(this.widthProperty());

        this.maxWidth(Double.MAX_VALUE);
        this.maxHeight(Double.MAX_VALUE);

        rect.setOnMouseClicked(this::handleClick);

        //X mark for crossed-off tiles / wrong guesses (color set depending on which)
        crossLineA.startXProperty().bind(rect.widthProperty().multiply(0.25));
        crossLineA.startYProperty().bind(rect.heightProperty().multiply(0.25));
        crossLineA.endXProperty().bind(rect.widthProperty().multiply(0.75));
        crossLineA.endYProperty().bind(rect.heightProperty().multiply(0.75));
        crossLineB.startXProperty().bind(rect.widthProperty().multiply(0.75));
        crossLineB.startYProperty().bind(rect.heightProperty().multiply(0.25));
        crossLineB.endXProperty().bind(rect.widthProperty().multiply(0.25));
        crossLineB.endYProperty().bind(rect.heightProperty().multiply(0.75));
        crossLineA.strokeWidthProperty().bind(rect.widthProperty().multiply(0.08));
        crossLineB.strokeWidthProperty().bind(rect.widthProperty().multiply(0.08));
        crossMark.setMouseTransparent(true);
        crossMark.setVisible(false);

        //outline copies share the same endpoints as the marks they sit behind, just wider and white
        crossOutlineA.startXProperty().bind(crossLineA.startXProperty());
        crossOutlineA.startYProperty().bind(crossLineA.startYProperty());
        crossOutlineA.endXProperty().bind(crossLineA.endXProperty());
        crossOutlineA.endYProperty().bind(crossLineA.endYProperty());
        crossOutlineB.startXProperty().bind(crossLineB.startXProperty());
        crossOutlineB.startYProperty().bind(crossLineB.startYProperty());
        crossOutlineB.endXProperty().bind(crossLineB.endXProperty());
        crossOutlineB.endYProperty().bind(crossLineB.endYProperty());
        crossOutlineA.strokeWidthProperty().bind(rect.widthProperty().multiply(0.16));
        crossOutlineB.strokeWidthProperty().bind(rect.widthProperty().multiply(0.16));
        crossOutlineA.setStroke(Color.WHITE);
        crossOutlineB.setStroke(Color.WHITE);

        //Circle mark for a correctly found target
        targetMark.centerXProperty().bind(rect.widthProperty().multiply(0.5));
        targetMark.centerYProperty().bind(rect.heightProperty().multiply(0.5));
        targetMark.radiusProperty().bind(rect.widthProperty().multiply(0.3));
        targetMark.setFill(Color.TRANSPARENT);
        targetMark.setStroke(Color.DARKGREEN);
        targetMark.strokeWidthProperty().bind(rect.widthProperty().multiply(0.06));
        targetMark.setMouseTransparent(true);
        targetMark.setVisible(false);

        targetOutline.centerXProperty().bind(targetMark.centerXProperty());
        targetOutline.centerYProperty().bind(targetMark.centerYProperty());
        targetOutline.radiusProperty().bind(targetMark.radiusProperty());
        targetOutline.setFill(Color.TRANSPARENT);
        targetOutline.setStroke(Color.WHITE);
        targetOutline.strokeWidthProperty().bind(rect.widthProperty().multiply(0.11));
        targetOutline.setMouseTransparent(true);
        targetOutline.visibleProperty().bind(found);

        crossMark.visibleProperty().bind(crossed);
        targetMark.visibleProperty().bind(found);

        this.getChildren().addAll(rect, crossMark, targetOutline, targetMark);
        setColor(level.colorFor(cell.colorGroup));
        setTarget(cell.target);
    }

    public int distance(int a,int b) {
        return Math.max(a,b) - Math.min(a,b);
    }

    public boolean isNeighbor(Tile other) {
        int rowDistance = distance(getRow(),other.getRow());
        int colDistance = distance(getCol(),other.getCol());
        return (rowDistance < 2 && colDistance < 2);
    }

    public void markTarget() {
        if(isTarget()) {
            found.set(true);
            solved.set(true);
            crossed.set(false);
            level.targetFound();
        } else {
            crossed.set(true);
            crossLineA.setStroke(Color.RED);
            crossLineB.setStroke(Color.RED);
            level.targetMissed();
        }
    }

    public void markCross() {
        crossed.set(!crossed.get());
        crossLineA.setStroke(Color.BLACK);
        crossLineB.setStroke(Color.BLACK);
    }

    private void handleClick(MouseEvent e) {
        if(solved.get()) { return; }
        if(e.getButton() == MouseButton.PRIMARY) {
            if (e.getClickCount() == 2) {
                markTarget();
            } else {
                markCross();
            }
        } else {
            markCross();
        }
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
        this.rect.setFill(color);
    }

    public boolean isTarget() {
        return target;
    }

    public void setTarget(boolean target) {
        this.target = target;
    }

    public void reset() {
        this.solved.set(false);
        this.crossed.set(false);
        this.found.set(false);
    }
}
