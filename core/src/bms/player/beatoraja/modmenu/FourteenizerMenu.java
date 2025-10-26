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
        private ImDouble asymptote;
        private ImDouble adherence;

        public Sigmoid(double inverseTime, double adherence, double asymptote) {
            this.inverseTime = new ImDouble(inverseTime);
            this.adherence = new ImDouble(adherence);
            this.asymptote = new ImDouble(asymptote);
        }
    }

    private static ImBoolean enabled = new ImBoolean(Fourteenizer.enabled);
    private static ImBoolean autoScratch = new ImBoolean(Fourteenizer.autoScratch);
    private static ImBoolean avoid56 = new ImBoolean(Fourteenizer.avoid56);
    private static ImBoolean avoidPills = new ImBoolean(Fourteenizer.avoidPills);
    private static ImInt scratchReallocationThreshold = new ImInt(Fourteenizer.scratchReallocationThreshold);
    private static ImInt avoidLNFactor = new ImInt(Fourteenizer.avoidLNFactor);
    private static Sigmoid hran = new Sigmoid(Fourteenizer.hran.inverseTime, Fourteenizer.hran.adherence, Fourteenizer.hran.asymptote);
    private static Sigmoid jacks = new Sigmoid(Fourteenizer.jacks.inverseTime, Fourteenizer.jacks.adherence, Fourteenizer.jacks.asymptote);
    private static Sigmoid murizara = new Sigmoid(Fourteenizer.murizara.inverseTime, Fourteenizer.murizara.adherence, Fourteenizer.murizara.asymptote);

    public static void show(ImBoolean showFourteenizer) {
        float relativeX = windowWidth * 0.455f;
        float relativeY = windowHeight * 0.04f;
        ImGui.setNextWindowPos(relativeX, relativeY, ImGuiCond.FirstUseEver);


        if(ImGui.begin("Fourteenizer", showFourteenizer, ImGuiWindowFlags.AlwaysAutoResize)) {
            if (ImGui.beginTable("FourteenizerSettingsTable", 5)) {
                ImGui.tableSetupColumn("##A");
                ImGui.tableSetupColumn("##B");
                ImGui.tableSetupColumn("##C");
                ImGui.tableSetupColumn("##D");
                ImGui.tableSetupColumn("##E");
                ImGui.tableNextRow();
                
                ImGui.tableSetColumnIndex(0);
                if (ImGui.checkbox("##enable", enabled)) {
                    Fourteenizer.enabled = enabled.get();
                }
                ImGui.tableSetColumnIndex(1);
                ImGui.text("Enable Fourteenizer");
                
                ImGui.tableSetColumnIndex(3);
                if (ImGui.checkbox("##avoidPills", avoidPills)) {
                    Fourteenizer.avoidPills = avoidPills.get();
                }
                ImGui.tableSetColumnIndex(4);
                ImGui.text("Avoid Pills");

                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                if (ImGui.checkbox("##autoScratch", autoScratch)) {
                    Fourteenizer.autoScratch = autoScratch.get();
                }
                ImGui.tableSetColumnIndex(1);
                ImGui.text("Auto Scratch");

                ImGui.tableSetColumnIndex(3);
                if (ImGui.checkbox("##avoid56", avoid56)) {
                    Fourteenizer.avoid56 = avoid56.get();
                }
                ImGui.tableSetColumnIndex(4);
                ImGui.text("Avoid 56");
                
                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                if (ImGui.dragScalar("##scratchReallocationThreshold", ImGuiDataType.S32, scratchReallocationThreshold, 1, 2, 7, "%d")) {
                    Fourteenizer.scratchReallocationThreshold = scratchReallocationThreshold.get();
                }
                ImGui.tableSetColumnIndex(1);
                ImGui.text("TT Reallocation");

                ImGui.tableSetColumnIndex(3);
                if (ImGui.dragScalar("##avoidLNFactor", ImGuiDataType.S32, avoidLNFactor, 1, 1, 5, "%d")) {
                    Fourteenizer.avoidLNFactor = avoidLNFactor.get();
                }
                ImGui.tableSetColumnIndex(4);
                ImGui.text("Avoid LN Factor");
            }
            ImGui.endTable();

            if (ImGui.beginTable("FourteenizerSigmoidTable", 5)) {
                ImGui.tableSetupColumn("Dimension");
                ImGui.tableSetupColumn("Spread");
                ImGui.tableSetupColumn("Adherence");
                ImGui.tableSetupColumn("Minimum");
                ImGui.tableSetupColumn("Zero Crossing");
                ImGui.tableHeadersRow();

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("H-ness of Random");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.dragScalar("##hranInverseTime", ImGuiDataType.Double, hran.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.hran.inverseTime = hran.inverseTime.get();
                }
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##hranAdherence", ImGuiDataType.Double, hran.adherence, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.hran.adherence = hran.adherence.get();
                }
                ImGui.tableSetColumnIndex(3);
                if (ImGui.dragScalar("##hranAsymptote", ImGuiDataType.Double, hran.asymptote, 0.01f, -1.0, 0.0, "%0.2f")) {
                    Fourteenizer.hran.asymptote = hran.asymptote.get();
                }
                ImGui.tableSetColumnIndex(4);
                ImGui.text(String.format("%.3f", Fourteenizer.hran.evaluateInverse(0.0)));

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Jack Protection");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.dragScalar("##jacksInverseTime", ImGuiDataType.Double, jacks.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.jacks.inverseTime = jacks.inverseTime.get();
                }
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##jacksAdherence", ImGuiDataType.Double, jacks.adherence, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.jacks.adherence = jacks.adherence.get();
                }
                ImGui.tableSetColumnIndex(3);
                if (ImGui.dragScalar("##jacksAsymptote", ImGuiDataType.Double, jacks.asymptote, 0.01f, -1.0, 0.0, "%0.2f")) {
                    Fourteenizer.jacks.asymptote = jacks.asymptote.get();
                }
                ImGui.tableSetColumnIndex(4);
                ImGui.text(String.format("%.3f", Fourteenizer.jacks.evaluateInverse(0.0)));

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Murizara Protection");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.dragScalar("##murizaraInverseTime", ImGuiDataType.Double, murizara.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.murizara.inverseTime = murizara.inverseTime.get();
                }
                ImGui.tableSetColumnIndex(2);
                if (ImGui.dragScalar("##murizaraAdherence", ImGuiDataType.Double, murizara.adherence, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.murizara.adherence = murizara.adherence.get();
                }
                ImGui.tableSetColumnIndex(3);
                if (ImGui.dragScalar("##murizaraAsymptote", ImGuiDataType.Double, murizara.asymptote, 0.01f, -1.0, 0.0, "%0.2f")) {
                    Fourteenizer.murizara.asymptote = murizara.asymptote.get();
                }
                ImGui.tableSetColumnIndex(4);
                ImGui.text(String.format("%.3f", Fourteenizer.murizara.evaluateInverse(0.0)));
            }
            ImGui.endTable();
        }
        ImGui.end();
    }
}
