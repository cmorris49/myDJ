package com.mydj.desktop.ui.util;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public final class FxUi {
    private FxUi() {}

    public static void setupEvenRow(HBox row, double spacing, double sidePadding) {
        row.setSpacing(spacing);
        row.setPadding(new Insets(0, sidePadding, 0, sidePadding));

        Platform.runLater(() -> {
            applyGrow(row);
            row.getChildren().addListener((ListChangeListener<Node>) c -> applyGrow(row));

            row.widthProperty().addListener((obs, ov, nv) -> {
                int count = row.getChildren().size();
                if (count == 0) return;

                double spacingPx = row.getSpacing() * (count - 1);
                Insets pad = row.getPadding();
                double padPx = (pad == null ? 0 : pad.getLeft() + pad.getRight());
                double per = Math.max(0, nv.doubleValue() - spacingPx - padPx) / count;

                for (Node n : row.getChildren()) {
                    if (n instanceof Region r) {
                        r.setMaxWidth(Double.MAX_VALUE);
                        r.setPrefWidth(per);
                    } else if (n instanceof Parent p) {
                        for (Node c2 : p.getChildrenUnmodifiable()) {
                            if (c2 instanceof Region r2) {
                                r2.setMaxWidth(Double.MAX_VALUE);
                                r2.setPrefWidth(per);
                                break;
                            }
                        }
                    }
                }
            });
        });
    }

    private static void applyGrow(HBox row) {
        for (Node n : row.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
            makeFillWidth(n);
        }
    }

    public static void makeFillWidth(Node n) {
        if (n instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        if (n instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                if (c instanceof Region r2) r2.setMaxWidth(Double.MAX_VALUE);
            }
        }
    }
}
