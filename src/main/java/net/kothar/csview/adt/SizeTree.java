package net.kothar.csview.adt;

/**
 * Stores sizes in a tree allowing quick lookup of size by item index and
 * the index at a given offset
 *
 * @author mhouston
 */
public class SizeTree {

    private int defaultSize;
    private Node root;

    public SizeTree(int defaultSize) {
        this.defaultSize = defaultSize;
        root = new Node();

        root.children = 0;
        root.size = 0;
    }

    public int getCount() {
        return root.children;
    }

    public void setCount(int count) {
        root.setChildren(count);
    }

    public int getTotal() {
        return root.size;
    }

    public int getSize(int index) {
        return root.getSize(index);
    }

    public void setSize(int index, int size) {
        root.setSize(index, size);
    }

    public int getPosition(int index) {
        return root.getPosition(index);
    }

    public int getItemAt(int pos) {
        return root.getItemAt(pos);
    }

    public SizeInfo getSizeInfoAt(int pos) {
        return root.getSizeInfoAt(pos);
    }

    public void add() {
        root.add();
    }

    public void add(int size) {
        root.add(size);
    }

    public class Node {

        int children;
        int size;
        int height;

        Node left;
        Node right;

        void setChildren(int count) {
            if (count == children)
                return;

            if (count > children)
                increaseChildren(count);
            else
                decreaseChildren(count);

            rebalance();
        }

        public void add(int size) {
            add();
            setSize(children - 1, size);
        }

        public void add() {
            setChildren(children + 1);
        }

        private void decreaseChildren(int count) {
            if (count == 0) {
                left = null;
                right = null;
                size = 0;
            }

            if (children == 0) {
                throw new IllegalArgumentException("Node already has 0 children");
            } else if (left == null) {
                // No child nodes, just reduce to new size
                assert size == children * defaultSize;
                children = count;
                size = children * defaultSize;
            } else {
                // Remove from children
                if (left.size > count) {
                    right.setChildren(0);
                    left.setChildren(count);
                } else {
                    right.setChildren(count - left.size);
                }
            }
        }

        private void increaseChildren(int count) {
            if (children == 0) {
                // Empty tree, start from scratch
                children = count;
                size = children * defaultSize;
            } else if (children == 1) {
                // Single item node, can we expand it?
                if (size == defaultSize) {
                    children = count;
                    size = children * defaultSize;
                } else {
                    // Split into child nodes
                    left = new Node();
                    left.children = 1;
                    left.size = size;

                    right = new Node();
                    right.children = count - 1;
                    right.size = right.children * defaultSize;

                    children = count;
                    size = left.size + right.size;
                }
            } else if (left == null) {
                // No child nodes, just expand
                assert size == children * defaultSize;
                children = count;
                size = children * defaultSize;
            } else {
                // Add to RHS
                right.setChildren(count - left.children);
            }
        }

        private void rebalance() {
            if (left == null) {
                return;
            }

            // TODO rebalance

            size = left.size + right.size;
            children = left.children + right.children;
            height = Math.max(left.height, right.height) + 1;
        }

        int getSize(int index) {
            if (index >= children) {
                throw new ArrayIndexOutOfBoundsException(index);
            }

            if (children == 1) {
                return size;
            } else if (left == null) {
                return defaultSize;
            } else if (index < left.children) {
                return left.getSize(index);
            } else {
                return right.getSize(index - left.children);
            }
        }

        void setSize(int index, int size) {
            if (index >= children) {
                throw new ArrayIndexOutOfBoundsException(index);
            }

            if (children == 1) {
                this.size = size;
            } else if (left == null) {
                if (index == 0) {
                    left = new Node();
                    left.children = 1;
                    left.size = size;

                    right = new Node();
                    right.children = children - 1;
                    right.size = right.children * defaultSize;
                } else {
                    left = new Node();
                    left.size = index * defaultSize;
                    left.children = index;

                    right = new Node();
                    right.children = children - index;
                    right.size = right.children * defaultSize;

                    right.setSize(0, size);
                }
            } else if (index < left.children) {
                left.setSize(index, size);
            } else {
                right.setSize(index - left.children, size);
            }

            rebalance();
        }

        int getPosition(int index) {
            if (index >= children) {
                throw new ArrayIndexOutOfBoundsException(index);
            }

            if (children == 1) {
                return 0;
            } else if (left == null) {
                return defaultSize * index;
            } else if (index < left.children) {
                return left.getPosition(index);
            } else {
                return left.size + right.getPosition(index - left.children);
            }
        }

        int getItemAt(int pos) {
            if (pos >= size) {
                throw new ArrayIndexOutOfBoundsException(pos);
            }

            if (children == 1) {
                return 0;
            } else if (left == null) {
                return pos / defaultSize;
            } else if (pos < left.size) {
                return left.getItemAt(pos);
            } else {
                return left.children + right.getItemAt(pos - left.size);
            }
        }

        SizeInfo getSizeInfoAt(int pos) {
            if (pos >= size) {
                throw new ArrayIndexOutOfBoundsException(pos);
            }

            if (children == 1) {
                return new SizeInfo(0, 0, size);
            } else if (left == null) {
                return new SizeInfo(pos / defaultSize, pos - (pos % defaultSize), defaultSize);
            } else if (pos < left.size) {
                return left.getSizeInfoAt(pos);
            } else {
                return right.getSizeInfoAt(pos - left.size).add(left.children, left.size);
            }
        }

    }

    public class SizeInfo {

        final int item;
        final int pos;
        final int size;

        SizeInfo(int item, int pos, int size) {
            this.item = item;
            this.pos = pos;
            this.size = size;
        }

        SizeInfo add(int items, int pos) {
            return new SizeInfo(item + items, this.pos + pos, size);
        }

        public int getItem() {
            return item;
        }

        public int getPos() {
            return pos;
        }

        public int getSize() {
            return size;
        }

        @Override
        public String toString() {
            return "SizeInfo{" +
                    "item=" + item +
                    ", pos=" + pos +
                    ", size=" + size +
                    '}';
        }
    }

    public static class Test {
        public static void main(String[] args) {
            SizeTree sizes = new SizeTree(32);

            sizes.setCount(10);
            assert (sizes.getTotal() == 320);

            sizes.setSize(5, 64);
            assert (sizes.getTotal() == 352);
            assert (sizes.getSize(5) == 64);

            sizes.add();
            assert (sizes.getTotal() == 384);

            sizes.add(10);
            assert (sizes.getTotal() == 394);

            print(sizes);
        }

        private static void print(SizeTree sizes) {
            for (int i = 0; i < sizes.getCount(); i++) {
                System.out.println("Item " + i + ": size = " + sizes.getSize(i) + ", position = " + sizes.getPosition(i));
            }
        }
    }
}
