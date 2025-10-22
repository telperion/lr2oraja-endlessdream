package bms.player.beatoraja.modmenu;

import imgui.ImColor;
import imgui.ImGui;
import imgui.flag.*;
import imgui.type.*;

import static bms.player.beatoraja.modmenu.ImGuiRenderer.*;
import bms.player.beatoraja.pattern.Fourteenizer;

public class FourteenizerMenu {
    public static class Sigmoid {
        private ImDouble inverseTime;
        private ImDouble offset;

        public Sigmoid(double inverseTime, double offset) {
            this.inverseTime = new ImDouble(inverseTime);
            this.offset = new ImDouble(offset);
        }

        public double evaluate(double x) {
            final double decimality = Math.pow(10.0, -offset.get());
            final double tightness = Math.log((1.0 - decimality) / decimality) / inverseTime.get();
            final double neg = Math.exp(-tightness *  x);
            final double pos = Math.exp( tightness * (x - inverseTime.get()));
            return 0.5 * (pos - neg) / (pos + neg) + 0.5;
        }
    }

    private static Sigmoid hran = new Sigmoid(Fourteenizer.hran.inverseTime, Fourteenizer.hran.offset);
    private static Sigmoid jacks = new Sigmoid(Fourteenizer.jacks.inverseTime, Fourteenizer.jacks.offset);
    private static Sigmoid murizara = new Sigmoid(Fourteenizer.murizara.inverseTime, Fourteenizer.murizara.offset);
    private static ImInt scratchReallocationThreshold = new ImInt(Fourteenizer.scratchReallocationThreshold);
    private static ImInt avoidLNFactor = new ImInt(Fourteenizer.avoidLNFactor);
    private static ImBoolean avoid56 = new ImBoolean(Fourteenizer.avoid56);
    private static ImBoolean avoidPills = new ImBoolean(Fourteenizer.avoidPills );

    public static void show(ImBoolean showFourteenizer) {
        float relativeX = windowWidth * 0.455f;
        float relativeY = windowHeight * 0.04f;
        ImGui.setNextWindowPos(relativeX, relativeY, ImGuiCond.FirstUseEver);


        if(ImGui.begin("Fourteenizer", showFourteenizer, ImGuiWindowFlags.AlwaysAutoResize)) {
            if (ImGui.beginTable("FourteenizerTable", 3, ImGuiTableFlags.Borders | ImGuiTableFlags.RowBg | ImGuiTableFlags.Resizable)) {
                ImGui.tableSetupColumn("Dimension");
                ImGui.tableSetupColumn("Time to Inverse");
                ImGui.tableSetupColumn("Offset");
                ImGui.tableHeadersRow();

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("H-ness of Random");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.dragScalar("##hranInverseTime", ImGuiDataType.Double, hran.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.hran.inverseTime = hran.inverseTime.get();
                }
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##hranOffset", ImGuiDataType.Double, hran.offset, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.hran.offset = hran.offset.get();
                }

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Jack Protection");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.dragScalar("##jacksInverseTime", ImGuiDataType.Double, jacks.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.jacks.inverseTime = jacks.inverseTime.get();
                }
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##jacksOffset", ImGuiDataType.Double, jacks.offset, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.jacks.offset = jacks.offset.get();
                }

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Murizara Protection");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.dragScalar("##murizaraInverseTime", ImGuiDataType.Double, murizara.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.murizara.inverseTime = murizara.inverseTime.get();
                }
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##murizaraOffset", ImGuiDataType.Double, murizara.offset, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.murizara.offset = murizara.offset.get();
                }

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("TT Reallocation");
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##scratchReallocationThreshold", ImGuiDataType.S32, scratchReallocationThreshold, 1, 3, 7, "%d")) {
                    Fourteenizer.scratchReallocationThreshold = scratchReallocationThreshold.get();
                }

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Avoid LN Factor");
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##avoidLNFactor", ImGuiDataType.S32, avoidLNFactor, 1, 1, 5, "%d")) {
                    Fourteenizer.avoidLNFactor = avoidLNFactor.get();
                }

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Avoid 56");
                ImGui.tableSetColumnIndex(2);
                if (ImGui.checkbox("##avoid56", avoid56)) {
                    Fourteenizer.avoid56 = avoid56.get();
                }

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Avoid Pills");
                ImGui.tableSetColumnIndex(2);
                if (ImGui.checkbox("##avoidPills", avoidPills)) {
                    Fourteenizer.avoidPills = avoidPills.get();
                }

                ImGui.endTable();
            }
            ImGui.end();
        }
    }
}
