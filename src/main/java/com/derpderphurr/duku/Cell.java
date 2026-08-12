package com.derpderphurr.duku;

//Plain data twin of Tile: a fixed board position plus the generation-time state assigned to it
//(colorGroup, target). No JavaFX, no rendering, no found/crossed - those are player-interaction
//state that only makes sense on Tile, not on the generated board itself.
final class Cell {
    static final int UNCLAIMED = -1;

    final int row, col;
    //Which region this cell was assigned to during generation - a plain index, not a rendered
    //color. Tile resolves it to an actual Color via its Level's palette (see Level.colorFor), so
    //swapping the palette (e.g. a future user-customizable stylesheet) never has to touch Cell.
    int colorGroup = UNCLAIMED;
    //Whether Solver proved this cell must be the target for its row/column/color. Set once, by
    //Solver.solve(), and never changes afterward - Tile reads it once at construction time.
    boolean target = false;

    Cell(int row, int col) {
        this.row = row;
        this.col = col;
    }

    boolean isNeighbor(Cell other) {
        return Math.abs(row - other.row) < 2 && Math.abs(col - other.col) < 2;
    }
}