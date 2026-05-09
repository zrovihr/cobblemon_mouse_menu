package com.cobblemonshortcuts;

import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection;
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CobblemonShortcutsClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon-shortcuts");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Cobblemon Shortcuts initialized");

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof BattleGUI battleGUI)) return;

            ScreenKeyboardEvents.afterKeyPress(screen).register((scr, key, scancode, modifiers) -> {
                BattleActionSelection selection = battleGUI.getCurrentActionSelection();
                if (selection == null) return;

                // Backtick (`) = Back button
                if (key == GLFW.GLFW_KEY_GRAVE_ACCENT) {
                    pressBack(selection);
                    return;
                }

                // Number keys 1-6
                int index = keyToIndex(key);
                if (index < 0) return;

                if (selection instanceof BattleGeneralActionSelection generalSelection) {
                    // Main action buttons: Fight, Switch, Catch, Run (1-4)
                    List<BattleOptionTile> tiles = generalSelection.getTiles();
                    if (index < tiles.size()) {
                        tiles.get(index).getOnClick().invoke();
                    }
                } else if (selection instanceof BattleMoveSelection moveSelection) {
                    // Move selection: up to 4 moves (1-4)
                    List<? extends BattleMoveSelection.MoveTile> moveTiles = moveSelection.getMoveTiles();
                    if (index < moveTiles.size()) {
                        BattleMoveSelection.MoveTile tile = moveTiles.get(index);
                        if (tile.getSelectable()) {
                            // In 2v2 or larger battles, selecting a move requires a target.
                            // The normal click path goes through BattleMoveSelection.mousePrimaryClicked()
                            // which shows BattleTargetSelection when pokemonPerSide > 1.
                            // We replicate that logic here to avoid a null target.
                            int pokemonPerSide = moveSelection.getRequest().getActivePokemon().getFormat().getBattleType().getPokemonPerSide();
                            if (pokemonPerSide > 1) {
                                // Transition to target selection instead of selecting immediately
                                battleGUI.changeActionSelection(new BattleTargetSelection(
                                    battleGUI,
                                    moveSelection.getRequest(),
                                    tile.getMove(),
                                    null,
                                    null
                                ));
                            } else {
                                tile.onClick();
                            }
                        }
                    }
                } else if (selection instanceof BattleSwitchPokemonSelection switchSelection) {
                    // Pokemon selection: up to 6 pokemon (1-6)
                    List<BattleSwitchPokemonSelection.SwitchTile> tiles = switchSelection.getTiles();
                    if (index < tiles.size()) {
                        BattleSwitchPokemonSelection.SwitchTile tile = tiles.get(index);
                        if (!tile.isFainted() && !tile.isCurrentlyInBattle()) {
                            // Simulate click at tile position
                            selection.mousePrimaryClicked(tile.getX() + 1, tile.getY() + 1);
                        }
                    }
                } else if (selection instanceof BattleTargetSelection targetSelection) {
                    // Target selection: pick a target pokemon for the selected move (2v2+ battles)
                    List<BattleTargetSelection.TargetTile> targetTiles = targetSelection.getTargetTiles();
                    int selectableIdx = 0;
                    for (BattleTargetSelection.TargetTile tile : targetTiles) {
                        if (tile.getSelectable()) {
                            if (selectableIdx == index) {
                                tile.onClick();
                                break;
                            }
                            selectableIdx++;
                        }
                    }
                }
            });
        });
    }

    private int keyToIndex(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_1 -> 0;
            case GLFW.GLFW_KEY_2 -> 1;
            case GLFW.GLFW_KEY_3 -> 2;
            case GLFW.GLFW_KEY_4 -> 3;
            case GLFW.GLFW_KEY_5 -> 4;
            case GLFW.GLFW_KEY_6 -> 5;
            default -> -1;
        };
    }

    private void pressBack(BattleActionSelection selection) {
        BattleBackButton backButton = null;

        if (selection instanceof BattleMoveSelection moveSelection) {
            backButton = moveSelection.getBackButton();
        } else if (selection instanceof BattleSwitchPokemonSelection switchSelection) {
            backButton = switchSelection.getBackButton();
        } else if (selection instanceof BattleGeneralActionSelection generalSelection) {
            backButton = generalSelection.getBackButton();
        } else if (selection instanceof BattleTargetSelection targetSelection) {
            backButton = targetSelection.getBackButton();
        }

        if (backButton != null) {
            // Simulate click on back button position
            selection.mousePrimaryClicked(backButton.getX() + 1, backButton.getY() + 1);
        }
    }
}
