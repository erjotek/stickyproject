# Sticky Mechanism - Deep Dive

## Overview
The sticky scroll mechanism keeps parent directories fixed at the top of the Project View as you scroll down, similar to sticky headers in code editors. It is implemented in two main files:
- `StickyScrollManager.kt` - The orchestrator handling installation, listeners, and lifecycle
- `StickyHeaderComponent.kt` - The custom Swing component that calculates and renders sticky headers

## Core Components
1. **StickyScrollManager**: Installs the sticky component, manages listeners for scroll/resize/tree events, handles autoscroll behavior, and controls visibility based on tool window state.
2. **StickyHeaderComponent**: A custom `JComponent` that calculates which directories should be sticky and paints them. Handles mouse clicks, hover effects, and drag & drop onto sticky headers.

## Architecture

### Component Placement
The `StickyHeaderComponent` is added to the `JLayeredPane` of the root pane with `POPUP_LAYER` z-index. This ensures:
- The component stays on top of the tree content without flickering
- Proper clipping to the viewport bounds
- Visibility is controlled based on tool window state

### Visibility Control
The component is hidden when:
- The scroll pane, viewport, or tree is not showing (`isShowing == false`)
- The Project View tool window is not visible
- The component bounds cannot be calculated

## Algorithm

### 1. Iterative Probing for Sticky Paths
The algorithm uses iterative probing to determine which directories should be sticky:

```
currentProbeY = visibleRect.y

while (stickyRows.size < maxStickyLimit):
    probeRow = tree.getClosestRowForLocation(0, currentProbeY + 1)
    candidates = getParentContainers(probeRow)  // from root to current
    
    for each candidate in candidates:
        if candidate already in stickyRows: continue
        if candidate is not descendant of last sticky: continue
        
        currentStickyBottom = visibleRect.y + (stickyRows.size * rowHeight)
        
        // A folder becomes sticky when its top edge touches or passes the sticky bottom
        if (candidateBounds.y <= currentStickyBottom):
            stickyRows.add(candidate)
            currentProbeY = visibleRect.y + (stickyRows.size * rowHeight)
            break  // Re-probe at new position
```

**Key insight**: The probe position moves down as sticky rows are added, ensuring deep hierarchies are captured correctly.

### 2. The "Push" Effect
To create a smooth transition where an incoming header pushes the existing stack up:
- Find the "next sibling or cousin" of the last sticky element
- Calculate the distance between the visual bottom of the sticky stack and the top of this next element
- If the next element is scrolling up and hitting the stack, calculate a `pushOffset`
- This `pushOffset` shifts the last sticky row upwards, creating the pushing animation

### 3. Rendering
The `StickyHeaderComponent` paints sticky rows in reverse order (deepest first) to handle overlays correctly:
- **Background**: Uses `FileColorManager` to get scope colors (loaded asynchronously to avoid SlowOperations on EDT, cached for performance)
- **Cell Rendering**: Uses the standard `tree.cellRenderer` to get the component for each node
- **Indentation**: Respects the original tree indentation
- **Hover Effect**: Adds a blue tint when hovering over a sticky row
- **Separators**: Draws a border line at the bottom of the stack

### 4. Background Color Caching
To avoid `SlowOperations` on the EDT, file colors are loaded asynchronously:
1. First render uses default tree background
2. Color is loaded in a pooled thread using `ReadAction`
3. Result is cached by file path
4. `repaint()` is called after loading to show the color

## Features

### Click Handling
Clicking on a sticky header:
- Selects the corresponding path in the tree
- Scrolls to make the path visible
- Skips autoscroll adjustment to prevent jumping

### Drag & Drop
Files can be dragged onto sticky headers to move them:
- Uses `MoveHandler.doMove()` for proper IntelliJ refactoring integration
- Supports multiple file selection
- Visual feedback with hover highlighting

### Autoscroll Adjustment
When a file is selected (e.g., via "Scroll to Source"), the scroll position is adjusted to ensure the file is visible below the sticky header stack, not hidden behind it.

## Installation & Lifecycle
- `StickyScrollManager` is installed via `MyProjectActivity` on project startup
- Uses `ToolWindowManagerListener` to detect when Project View becomes available
- Uses a `Timer` to handle lazy initialization and view switching (Project, Packages, etc.)
- Properly detaches listeners and removes component on dispose

## Key Listeners
- `AdjustmentListener` - Scroll bar changes
- `ComponentListener` - Resize events
- `ChangeListener` - Viewport changes
- `TreeModelListener` - Node changes (with special handling for affected paths)
- `TreeExpansionListener` - Expand/Collapse events
- `TreeSelectionListener` - Selection changes for autoscroll
