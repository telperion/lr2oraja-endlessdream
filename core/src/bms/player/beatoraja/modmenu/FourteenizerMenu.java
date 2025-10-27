package bms.player.beatoraja.modmenu;

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

    private static void toolTip(String text) {
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip();
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + 300.0f); 
            ImGui.textUnformatted(text);
            ImGui.popTextWrapPos();
            ImGui.endTooltip();
        }
    }

    public static void show(ImBoolean showFourteenizer) {
        float relativeX = windowWidth * 0.455f;
        float relativeY = windowHeight * 0.04f;
        ImGui.setNextWindowPos(relativeX, relativeY, ImGuiCond.FirstUseEver);

        if(ImGui.begin("Fourteenizer v" + Fourteenizer.VERSION, showFourteenizer, ImGuiWindowFlags.AlwaysAutoResize)) {
            if (ImGui.beginTable("FourteenizerSettingsTable", 2)) {
                ImGui.tableSetupColumn("##A");
                ImGui.tableSetupColumn("##B");
                ImGui.tableNextRow();
                
                ImGui.tableSetColumnIndex(0);
                if (ImGui.checkbox("Enable Fourteenizer", enabled)) {
                    Fourteenizer.enabled = enabled.get();
                }
                toolTip("When enabled, the Fourteenizer algorithm overrides the DP BATTLE option.");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.checkbox("Avoid Pills", avoidPills)) {
                    Fourteenizer.avoidPills = avoidPills.get();
                }
                toolTip("Avoid placing chords with notes in adjacent lanes (12 and 67 are permitted).");
                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                if (ImGui.checkbox("Auto Scratch", autoScratch)) {
                    Fourteenizer.autoScratch = autoScratch.get();
                }
                toolTip("Reallocate scratch notes to key lanes.");
                ImGui.tableSetColumnIndex(1);
                if (ImGui.checkbox("Avoid 56", avoid56)) {
                    Fourteenizer.avoid56 = avoid56.get();
                }
                toolTip("Avoid placing 56 (ring finger) chords.");
                ImGui.tableNextRow();

                ImGui.tableSetColumnIndex(0);
                ImGui.setNextItemWidth(ImGui.getTextLineHeight());
                if (ImGui.dragScalar("TT Reallocation", ImGuiDataType.S32, scratchReallocationThreshold, 1, 2, 7, "%d")) {
                    Fourteenizer.scratchReallocationThreshold = scratchReallocationThreshold.get();
                }
                toolTip("The Fourteenizer algorithm will not place notes on the same side as a simultaneous scratch. If the number of key notes exceeds this threshold, reallocate the simultaneous scratch to a key lane, then rerun the algorithm.");
                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getTextLineHeight());
                if (ImGui.dragScalar("Avoid LN Factor", ImGuiDataType.S32, avoidLNFactor, 1, 0, 5, "%d")) {
                    Fourteenizer.avoidLNFactor = avoidLNFactor.get();
                }
                toolTip("The Fourteenizer algorithm avoids placing short key notes on the same side as a key LN. The higher the Avoid LN factor, the less likely short notes are to combine with LNs.");
                ImGui.tableNextRow();
            }
            ImGui.endTable();

            if (ImGui.beginTable("FourteenizerSigmoidTable", 5)) {
                ImGui.tableSetupColumn("Dimension");
                ImGui.tableSetupColumn("Spread");
                ImGui.tableSetupColumn("Adherence");
                ImGui.tableSetupColumn("Minimum");
                ImGui.tableSetupColumn("Zero Point");
                ImGui.tableHeadersRow();

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("H-ness of Random");
                toolTip("When choosing which lane to place a note, the Fourteenizer algorithm attempts to correlate with recent appearances of similar notes (calculated by filename similarity). As the time since the prior note increases and the H-ness of Random rises, the less Fourteenizer pays attention to note similarity.");
                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##hranInverseTime", ImGuiDataType.Double, hran.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.hran.inverseTime = hran.inverseTime.get();
                }
                toolTip("Higher values lengthen the probability function.");
                ImGui.tableSetColumnIndex(2);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##hranAdherence", ImGuiDataType.Double, hran.adherence, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.hran.adherence = hran.adherence.get();
                }
                toolTip("Higher values create a sharper transition in the probability function.");
                ImGui.tableSetColumnIndex(3);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##hranAsymptote", ImGuiDataType.Double, hran.asymptote, 0.01f, -1.0, 0.0, "%0.2f")) {
                    Fourteenizer.hran.asymptote = hran.asymptote.get();
                }
                toolTip("Pull the probability density function's starting point below zero.");
                ImGui.tableSetColumnIndex(4);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                ImGui.text(String.format("%.3f", Fourteenizer.hran.evaluateInverse(0.0)) + " sec.");
                toolTip("Time since the prior note in this lane before the algorithm begins to disengage note correlation.");

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Jack Protection");
                toolTip("The Fourteenizer algorithm avoids placing key notes too soon after the prior note in the same lane.");
                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##jacksInverseTime", ImGuiDataType.Double, jacks.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.jacks.inverseTime = jacks.inverseTime.get();
                }
                toolTip("By this time since the prior note in this lane, jack protection is effectively disengaged.");
                ImGui.tableSetColumnIndex(2);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##jacksAdherence", ImGuiDataType.Double, jacks.adherence, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.jacks.adherence = jacks.adherence.get();
                }
                toolTip("Higher values create a sharper transition in the probability function.");
                ImGui.tableSetColumnIndex(3);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##jacksAsymptote", ImGuiDataType.Double, jacks.asymptote, 0.01f, -1.0, 0.0, "%0.2f")) {
                    Fourteenizer.jacks.asymptote = jacks.asymptote.get();
                }
                toolTip("Pull the probability density function's starting point below zero.");
                ImGui.tableSetColumnIndex(4);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                ImGui.text(String.format("%.3f", Fourteenizer.jacks.evaluateInverse(0.0)) + " sec.");
                toolTip("Time since the prior note in this lane during which jack protection is fully engaged.");

                ImGui.tableNextRow();
                ImGui.tableSetColumnIndex(0);
                ImGui.text("Murizara Protection");
                toolTip("The Fourteenizer algorithm avoids placing key notes too soon after a scratch note on the same side.");
                ImGui.tableSetColumnIndex(1);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##murizaraInverseTime", ImGuiDataType.Double, murizara.inverseTime, 0.1f, 0.1, 1000.0, "%0.1f")) {
                    Fourteenizer.murizara.inverseTime = murizara.inverseTime.get();
                }
                toolTip("By this time since the prior note in this lane, murizara protection is effectively disengaged.");
                ImGui.tableSetColumnIndex(2);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##murizaraAdherence", ImGuiDataType.Double, murizara.adherence, 0.1f, 0.1, 10.0, "%0.1f")) {
                    Fourteenizer.murizara.adherence = murizara.adherence.get();
                }
                toolTip("Higher values create a sharper transition in the probability function.");
                ImGui.tableSetColumnIndex(3);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                if (ImGui.dragScalar("##murizaraAsymptote", ImGuiDataType.Double, murizara.asymptote, 0.01f, -1.0, 0.0, "%0.2f")) {
                    Fourteenizer.murizara.asymptote = murizara.asymptote.get();
                }
                toolTip("Pull the probability density function's starting point below zero.");
                ImGui.tableSetColumnIndex(4);
                ImGui.setNextItemWidth(ImGui.getColumnWidth());
                ImGui.text(String.format("%.3f", Fourteenizer.murizara.evaluateInverse(0.0)) + " sec.");
                toolTip("Time since the prior note in this lane during which murizara protection is fully engaged.");
            }
            ImGui.endTable();
        }
        ImGui.end();
    }
}
