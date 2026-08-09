package com.derpderphurr.duku;

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
    private final Group crossMark = new Group(crossLineA, crossLineB);
    private final Circle targetMark = new Circle();
    private Color color = DEFAULT_COLOR;

    private final Level level;

    //Logic
    //Target is wether this tile is a target
    private boolean target = false;
    //solved is used for if a user marks as a target and true, show the target in cell
    //if user marks as target and target is false, set the cross flag and solved flag
    private boolean solved = false; // works with target
    private boolean crossed = false; // when a user marks as not a target

    //where the cell lives on the grid
    private final int row,col;

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public Tile(Level l,int x,int y) {
        this.row = y;
        this.col = x;
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

        //Circle mark for a correctly found target
        targetMark.centerXProperty().bind(rect.widthProperty().multiply(0.5));
        targetMark.centerYProperty().bind(rect.heightProperty().multiply(0.5));
        targetMark.radiusProperty().bind(rect.widthProperty().multiply(0.3));
        targetMark.setFill(Color.TRANSPARENT);
        targetMark.setStroke(Color.DARKGREEN);
        targetMark.strokeWidthProperty().bind(rect.widthProperty().multiply(0.06));
        targetMark.setMouseTransparent(true);
        targetMark.setVisible(false);

        this.getChildren().addAll(rect, crossMark, targetMark);
        setColor(color);
    }

    public void markTarget() {
        if(!solved) {
            solved = true;
            if(isTarget()) {
                crossMark.setVisible(false);
                targetMark.setVisible(true);
            } else {
                crossed = true;
                crossLineA.setStroke(Color.RED);
                crossLineB.setStroke(Color.RED);
                crossMark.setVisible(true);
            }
        }
        //ignore marking an already solved cell
    }

    public int distance(int a,int b) {
        return Math.max(a,b) - Math.min(a,b);
    }

    public boolean isNeighbor(Tile other) {
        int rowDistance = distance(getRow(),other.getRow());
        int colDistance = distance(getCol(),other.getCol());
        return (rowDistance < 2 && colDistance < 2);
    }

    public void markCross() {
        if(!solved) {
            crossed = !crossed;
            crossLineA.setStroke(Color.BLACK);
            crossLineB.setStroke(Color.BLACK);
            crossMark.setVisible(crossed);
        }
    }

    private void handleClick(MouseEvent e) {
        System.out.printf("Handled Click (%d,%d) :: %s%n",col,row,color.toString());
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

}
