package net.kothar.csview.adt;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SizeTreeTest {

    private static final int DEFAULT = 20;

    @Test
    public void growsWithDefaultSizes() {
        SizeTree tree = new SizeTree(DEFAULT);
        tree.setCount(10);
        assertEquals(10, tree.getCount());
        assertEquals(200, tree.getTotal());
    }

    @Test
    public void emptyingATreeLeavesItEmpty() {
        SizeTree tree = new SizeTree(DEFAULT);
        tree.setCount(10);
        tree.setCount(0);
        assertEquals(0, tree.getCount());
        assertEquals(0, tree.getTotal());
    }

    @Test
    public void shrinkingALeafDropsTheRemovedItems() {
        SizeTree tree = new SizeTree(DEFAULT);
        tree.setCount(10);
        tree.setCount(3);
        assertEquals(3, tree.getCount());
        assertEquals(60, tree.getTotal());
    }

    /**
     * Shrinking used to pick the subtree to trim by comparing the item count against the left
     * subtree's total <em>size</em>, so any tree holding a non-default size took the wrong branch
     * and quietly lost it. Column widths live in one of these.
     */
    @Test
    public void shrinkingKeepsTheSizesOfTheItemsThatRemain() {
        SizeTree tree = new SizeTree(DEFAULT);
        tree.setCount(10);
        tree.setSize(2, 64);

        tree.setCount(8);

        assertEquals(8, tree.getCount());
        assertEquals("item 2 keeps its width", 64, tree.getSize(2));
        assertEquals("7 default items plus the resized one", 7 * DEFAULT + 64, tree.getTotal());
    }

    @Test
    public void shrinkingPastAResizedItemDropsIt() {
        SizeTree tree = new SizeTree(DEFAULT);
        tree.setCount(10);
        tree.setSize(5, 64);

        tree.setCount(3);

        assertEquals(3, tree.getCount());
        assertEquals(3 * DEFAULT, tree.getTotal());
    }

    @Test
    public void positionsFollowTheSizesAfterShrinking() {
        SizeTree tree = new SizeTree(DEFAULT);
        tree.setCount(10);
        tree.setSize(2, 64);
        tree.setCount(8);

        assertEquals(0, tree.getPosition(0));
        assertEquals(DEFAULT, tree.getPosition(1));
        assertEquals(2 * DEFAULT, tree.getPosition(2));
        assertEquals(2 * DEFAULT + 64, tree.getPosition(3));
        // position(7) is the sum of items 0-6: two default, the resized one, then four default
        assertEquals(6 * DEFAULT + 64, tree.getPosition(7));
    }

    @Test
    public void addKeepsRunningTotal() {
        SizeTree tree = new SizeTree(32);
        tree.setCount(10);
        tree.setSize(5, 64);
        assertEquals(352, tree.getTotal());
        tree.add();
        assertEquals(384, tree.getTotal());
        tree.add(10);
        assertEquals(394, tree.getTotal());
    }
}
