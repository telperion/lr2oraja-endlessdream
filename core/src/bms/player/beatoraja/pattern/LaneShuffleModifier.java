package bms.player.beatoraja.pattern;

import bms.model.*;

import java.util.*;

import bms.player.beatoraja.modmenu.RandomTrainer;
import bms.player.beatoraja.PlayerConfig;

import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.badlogic.gdx.utils.IntArray;

/**
 * レーン単位でノーツを入れ替えるオプション
 *
 * @author exch
 */
public abstract class LaneShuffleModifier extends PatternModifier {

	/**
	 * 各レーンの移動先
	 */
	private int[] random;
	/**
	 * 変更レーンにスクラッチレーンを含むか
	 */
	public final boolean isScratchLaneModify;

	public final boolean showShufflePattern;

	public LaneShuffleModifier(int player, boolean isScratchLaneModify, boolean showShufflePattern) {
		super(player);
		this.isScratchLaneModify = isScratchLaneModify;
		this.showShufflePattern = showShufflePattern;
	}

	protected abstract int[] makeRandom(int[] keys, BMSModel model);

	@Override
	public void modify(BMSModel model) {
		Mode mode = model.getMode();
		final int[] keys = getKeys(mode, player, isScratchLaneModify);
		if(keys.length == 0) {
			return;
		}
		random = makeRandom(keys, model);

        // Random Trainer History
        if (random.length == 8) {
            StringBuilder randomSb = new StringBuilder();
            for (int i = 0; i < random.length - 1; i++) {
                randomSb.append(Integer.toString(random[i] + 1));
            }
            RandomTrainer rt = new RandomTrainer();
            RandomTrainer.addRandomHistory(
                    rt.new RandomHistoryEntry(model.getTitle(), randomSb.toString())
            );
        }

		final int lanes = model.getMode().key;
		final Note[] notes = new Note[lanes];
		final Note[] hnotes = new Note[lanes];
		final boolean[] clone = new boolean[lanes];
		TimeLine[] timelines = model.getAllTimeLines();
		for (int index = 0; index < timelines.length; index++) {
			final TimeLine tl = timelines[index];
			if (tl.existNote() || tl.existHiddenNote()) {
				for (int i = 0; i < lanes; i++) {
					notes[i] = tl.getNote(i);
					hnotes[i] = tl.getHiddenNote(i);
					clone[i] = false;
				}
				for (int i = 0; i < lanes; i++) {
					final int mod = i < random.length ? random[i] : i;
					if (clone[mod]) {
						if (notes[mod] != null) {
							if (notes[mod] instanceof LongNote && ((LongNote) notes[mod]).isEnd()) {
								for (int j = index - 1; j >= 0; j--) {
									if (((LongNote) notes[mod]).getPair().getSection() == timelines[j].getSection()) {
										LongNote ln = (LongNote) timelines[j].getNote(i);
										tl.setNote(i, ln.getPair());
//										System.out.println(ln.toString() + " : " + ln.getPair().toString() + " == "
//												+ ((LongNote) notes[mod]).getPair().toString() + " : "
//												+ notes[mod].toString());
										break;
									}
								}
							} else {
								tl.setNote(i, (Note) notes[mod].clone());
							}
						} else {
							tl.setNote(i, null);
						}
						if (hnotes[mod] != null) {
							tl.setHiddenNote(i, (Note) hnotes[mod].clone());
						} else {
							tl.setHiddenNote(i, null);
						}
					} else {
						tl.setNote(i, notes[mod]);
						tl.setHiddenNote(i, hnotes[mod]);
						clone[mod] = true;
					}
				}
			}
		}
	}

	public boolean isToDisplay() {
		return showShufflePattern;
	}

	public int[] getRandomPattern(Mode mode) {
		int keys = mode.key / mode.player;
		int[] repr = new int[keys];
		if(showShufflePattern) {
			if (mode.scratchKey.length > 0 && !isScratchLaneModify) { // BEAT-*K
				System.arraycopy(random, keys * player, repr, 0, keys - 1);
				repr[keys - 1] = mode.scratchKey[player];
			} else {
				System.arraycopy(random, keys * player, repr, 0, keys);
			}
		}
		return repr;
	}

	public static class LaneMirrorShuffleModifier extends LaneShuffleModifier {

		public LaneMirrorShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, false);
			setAssistLevel(isScratchLaneModify ? AssistLevel.LIGHT_ASSIST : AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int lane = 0; lane < keys.length; lane++) {
				result[keys[lane]] = keys[keys.length - 1 - lane];
			}
			return result;
		}
	}

	public static class LaneRotateShuffleModifier extends LaneShuffleModifier {

		public LaneRotateShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(isScratchLaneModify ? AssistLevel.LIGHT_ASSIST : AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			java.util.Random rand = new java.util.Random(getSeed());
			final boolean inc = (rand.nextInt(2) == 1);
			final int start = rand.nextInt(keys.length - 1) + (inc ? 1 : 0);
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int lane = 0, rlane = start; lane < keys.length; lane++) {
				result[keys[lane]] = keys[rlane];
				rlane = inc ? (rlane + 1) % keys.length : (rlane + keys.length - 1) % keys.length;
			}
			return result;
		}
	}

	public static class LaneRandomShuffleModifier extends LaneShuffleModifier {

		public LaneRandomShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(isScratchLaneModify ? AssistLevel.LIGHT_ASSIST : AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			java.util.Random rand = new java.util.Random(getSeed());
			IntArray l = new IntArray(keys);
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int lane = 0; lane < keys.length; lane++) {
				int r = rand.nextInt(l.size);
				result[keys[lane]] = l.get(r);
				l.removeIndex(r);
			}
			return result;
		}
	}

	public static class PlayerFlipModifier extends LaneShuffleModifier {

		public PlayerFlipModifier() {
			super(0, true, false);
			setAssistLevel(AssistLevel.NONE);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			if (model.getMode().player == 2) {
				for (int i = 0; i < result.length; i++) {
					result[i] = (i + result.length / 2) % result.length;
				}
			}
			return result;
		}
	}

	public static class PlayerBattleModifier extends LaneShuffleModifier {

		public PlayerBattleModifier() {
			super(0, true, false);
			setAssistLevel(AssistLevel.ASSIST);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			if (model.getMode().player == 1) {
				return new int[0];
			} else {
				int[] result = new int[keys.length * 2];
				System.arraycopy(keys, 0, result, 0, keys.length);
				System.arraycopy(keys, 0, result, keys.length, keys.length);
				setAssistLevel(AssistLevel.ASSIST);
				return result;
			}
		}
	}

	public enum FourteenizerRegion {
		P1_TT,
		P1_KEY,
		P2_KEY,
		P2_TT,
	}

	public FourteenizerRegion l2r(int lane) {
		if (lane == 7) {
			return FourteenizerRegion.P1_TT;
		}
		if (lane == 15) {
			return FourteenizerRegion.P2_TT;
		}
		if (lane < 7) {
			return FourteenizerRegion.P1_KEY;
		}
		return FourteenizerRegion.P2_KEY;
	}
	
	public final boolean isScratchLane(int lane) {
		return l2r(lane) == FourteenizerRegion.P1_TT || l2r(lane) == FourteenizerRegion.P2_TT;
	}

	public final int scratchFor(int lane) {
		if (l2r(lane) == FourteenizerRegion.P1_KEY) {
			return 7;
		}
		if (l2r(lane) == FourteenizerRegion.P2_KEY) {
			return 15;
		}
		return lane;
	}

	public final int playerIndex(int lane) {
		return (lane > 7) ? 1 : 0;
	}

	public final int normalize(int lane) {
		if (lane == 15) {
			return 7;
		}
		return (lane > 7) ? 14 - lane : lane;
	}

	public class LaneData {
		public Note note;
		public LongNote head;
		public int source;
		public boolean hran;

		public LaneData() {
			this.note = null;
			this.head = null;
			this.source = -1;
			this.hran = false;
		}

		public final double since(long time) {
			if (head != null) {
				return 0.0;
			}
			if (note != null) {
				return (time - note.getTime()) / 1000.0;
			}
			return time / 1000.0;
		}

		public final boolean activeLN() {
			return head != null;
		}
	}

	public class FiveFingerFavorability {
		public Map<Integer, Double> fff;

		public final void initialize() {
			fff.clear();
			fff.put(124,    5.0); // 0,0,1,1,1,1,1
			fff.put(122,    2.0); // 0,1,0,1,1,1,1
			fff.put(118,    1.0); // 0,1,1,0,1,1,1
			fff.put(110,    1.0); // 0,1,1,1,0,1,1
			fff.put( 94,    1.0); // 0,1,1,1,1,0,1
			fff.put( 62,    1.0); // 0,1,1,1,1,1,0
			fff.put(121,    2.0); // 1,0,0,1,1,1,1
			fff.put(117,    5.0); // 1,0,1,0,1,1,1
			fff.put(109,   10.0); // 1,0,1,1,0,1,1
			fff.put( 93,   50.0); // 1,0,1,1,1,0,1
			fff.put( 61,   10.0); // 1,0,1,1,1,1,0
			fff.put(115,    2.0); // 1,1,0,0,1,1,1
			fff.put(107, 1000.0); // 1,1,0,1,0,1,1
			fff.put( 91,   10.0); // 1,1,0,1,1,0,1
			fff.put( 59,  100.0); // 1,1,0,1,1,1,0
			fff.put(103,    1.0); // 1,1,1,0,0,1,1
			fff.put( 87,    1.0); // 1,1,1,0,1,0,1
			fff.put( 55,    1.0); // 1,1,1,0,1,1,0
			fff.put( 79,    1.0); // 1,1,1,1,0,0,1
			fff.put( 47,    1.0); // 1,1,1,1,0,1,0
			fff.put( 31,    5.0); // 1,1,1,1,1,0,0
			fff.put( 85, 1000.0); // 1,0,1,0,1,0,1
			fff.put( 27,  700.0); // 1,1,0,1,1,0,0
			fff.put(108,  700.0); // 0,0,1,1,0,1,1
			fff.put(120,   50.0); // 0,0,0,1,1,1,1
			fff.put( 60,   50.0); // 0,0,1,1,1,1,0
			fff.put( 82,  300.0); // 0,1,0,0,1,0,1
			fff.put( 37,  300.0); // 1,0,1,0,0,1,0
		}

		public FiveFingerFavorability() {
			fff = new HashMap<>();
			initialize();
		}

		private final void removeLane(int lane) {
			if (isScratchLane(lane)) {
				return;
			}

			// Remove this lane from all keys in the five-finger favorability map.
			// Any keys reduced to 0 (no lanes) are removed from the map entirely.
			final int bit = 1 << normalize(lane);
			Map<Integer, Double> fffAfterRemoval = fff.entrySet().stream().map(entry -> {
				int key = entry.getKey();
				if ((key & bit) == 0) {
					// If this arrangement doesn't have the lane, we can't use it.
					return null;
				}
				if ((key & ~bit) == 0) {
					// If all this arrangement has left is the lane, we can't use it.
					return null;
				}
				key &= ~bit;
				return Map.entry(key, entry.getValue());
			}).filter(entry -> entry != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			fff = fffAfterRemoval;
		}

		private final boolean hasMatching(int lane) {
			for (Map.Entry<Integer, Double> entry : fff.entrySet()) {
				if ((entry.getKey() & (1 << normalize(lane))) != 0) {
					return true;
				}
			}
			return false;
		}

		private final void applyPDF(int lane, double pdf) {
			if (isScratchLane(lane)) {
				return;
			}

			final int bit = 1 << normalize(lane);
			Map<Integer, Double> fffAfterPDF = fff.entrySet().stream().map(entry -> {
				final int key = entry.getKey();
				if ((key & bit) == 0) {
					return entry;
				}
				return Map.entry(key, entry.getValue() * pdf);
			}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			fff = fffAfterPDF;
		}

		private final double sumPDF(int lane) {
			return fff.entrySet().stream()
				.filter(entry -> (entry.getKey() & (1 << normalize(lane))) != 0)
				.mapToDouble(Map.Entry::getValue)
				.sum();
		}

		private final int maxLaneCount() {
			return fff.entrySet().stream().map(entry -> {
				int key = entry.getKey();
				int count = 0;
				for (int i = 0; i < 7; i++) {
					if ((key & (1 << i)) != 0) {
						count++;
					}
				}
				return count;
			}).max(Comparator.naturalOrder()).orElse(0);
		}

		public static final Set<Integer> convert(int code) {
			// Expand the bitset into individual lanes.
			Set<Integer> result = new HashSet<>();
			for (int i = 0; i < 7; i++) {
				if ((code & (1 << i)) != 0) {
					result.add(i);
				}
			}
			return result;
		}

		private final Set<Integer> select(int minCount, java.util.Random rand) {
			// Filter out FFF options without enough lanes.
			Map<Integer, Double> fffSufficient = fff.entrySet().stream().map(entry -> {
				int key = entry.getKey();
				int count = 0;
				for (int i = 0; i < 7; i++) {
					if ((key & (1 << i)) != 0) {
						count++;
					}
				}
				if (count < minCount) {
					return null;
				}
				return Map.entry(key, entry.getValue());
			}).filter(entry -> entry != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

			// If no valid options remain after filtering, return empty set.
			if (fffSufficient.isEmpty()) {
				return new HashSet<>();
			}

			// Select from the remaining options based on their favorability.
			double favorSum = fffSufficient.values().stream().mapToDouble(Double::doubleValue).sum();
			if (favorSum <= 0.0) {
				return new HashSet<>();
			}
			
			double r = rand.nextDouble();
			int fiveFinger = 0;
			for (Map.Entry<Integer, Double> entry : fff.entrySet()) {
				r -= entry.getValue() / favorSum;
				if (r <= 0.0) {
					fiveFinger = entry.getKey();
					break;
				}
			}

			return convert(fiveFinger);
		}
	}

	public class FourteenizerState {
		private BMSModel model;
		private PlayerConfig config;
		private static final int LANES = 16;
		private LaneData[] data;
		private FiveFingerFavorability[] fff;
		private Map<Integer, EnumSet<FourteenizerRegion>> allocation;
		private Map<Integer, Integer> permuter;
		private java.util.Random rand;
		private int count;
		
		public static final double sigmoid(double x, double inversion_time, double offset_at_zero) {
			final double decimality = Math.pow(10.0, -offset_at_zero);
			final double tightness = Math.log((1.0 - decimality) / decimality) / inversion_time;
			final double neg = Math.exp(-tightness *  x);
			final double pos = Math.exp( tightness * (x - inversion_time));
			return 0.5 * (pos - neg) / (pos + neg) + 0.5;
		}

		public static final int levenshteinDistance(String s1, String s2) {
			int[][] dp = new int[s1.length() + 1][s2.length() + 1];
			for (int i = 0; i <= s1.length(); i++) {
				dp[i][0] = i;
			}
			for (int j = 0; j <= s2.length(); j++) {
				dp[0][j] = j;
			}
			for (int i = 1; i <= s1.length(); i++) {
				for (int j = 1; j <= s2.length(); j++) {
					dp[i][j] = Math.min(dp[i-1][j-1] + (s1.charAt(i-1) == s2.charAt(j-1) ? 0 : 1), Math.min(dp[i-1][j] + 1, dp[i][j-1] + 1));
				}
			}
			return dp[s1.length()][s2.length()];
		}

		private final double levenshteinDistance(Note n1, Note n2) {
			if (n1 == null || n2 == null) {
				return 1.0;
			}
			int w1 = n1.getWav();
			int w2 = n2.getWav();
			if (w1 < 0 || w2 < 0) {
				return 1.0;
			}
			String fn1 = model.getWavList()[w1];
			String fn2 = model.getWavList()[w2];
			return ((double) levenshteinDistance(fn1, fn2)) / Math.max(fn1.length(), fn2.length());
		}

		// Time since the last note in the same lane
		// Applied on key lanes only
		private final boolean impactJacks(int laneTarget, int laneTest) {
			return (laneTarget == laneTest);
		}

		// Time since the last note in the scratch lane
		// Applied on key lanes only
		private final boolean impactMurizara(int laneTarget, int laneTest) {
			if (isScratchLane(laneTarget)) {
				return false;
			}
			if (l2r(laneTarget) == FourteenizerRegion.P1_KEY) {
				// Check P1 turntable.
				return (l2r(laneTest) == FourteenizerRegion.P1_TT);
			}
			// Check P2 turntable.
			return (l2r(laneTest) == FourteenizerRegion.P2_TT);
		}

		// So you're saying there's a chance.
		private final double MIN_PDF = 1e-24;

		
		public FourteenizerState(long seed, PlayerConfig config, BMSModel model) {
			this.config = config;
			this.model = model;
			this.data = new LaneData[LANES];
			for (int i = 0; i < LANES; i++) {
				this.data[i] = new LaneData();
			}
			this.fff = new FiveFingerFavorability[2];
			this.fff[0] = new FiveFingerFavorability();
			this.fff[1] = new FiveFingerFavorability();
			this.allocation = new HashMap<>();
			this.permuter = new HashMap<>();
			this.rand = new java.util.Random(seed);
			this.count = 0;
		}

		private boolean anyHistory() {
			for (int i = 0; i < LANES; i++) {
				if (data[i].note != null) {
					return true;
				}
			}
			return false;
		}

		private double pdfJack(int lane, long time) {
			if (isScratchLane(lane)) {
				// Early exit - scratch lanes don't care about "jacks".
				return 1.0;
			}
			if (permuter.containsValue(lane)) {
				// Early exit - already receiving a note this update.
				return 0.0;
			}
			if (!anyHistory()) {
				// Early exit - no note history to influence positioning yet.
				return 1.0;
			}
			// Calculate the time since the last note in the same lane,
			// and use that to calculate the sigmoid influence on the PDF.
			return sigmoid(
				data[lane].since(time),
				config.getJacksInverseTime(),
				config.getJacksOffset()
			);
		}

		private double pdfMurizara(int lane, long time) {
			if (isScratchLane(lane)) {
				// Early exit - scratch lanes have a different calculation for murizara.
				return 1.0;
			}
			if (permuter.containsValue(lane)) {
				// Early exit - already receiving a note this update.
				return 0.0;
			}
			if (!anyHistory()) {
				// Early exit - no note history to influence positioning yet.
				return 1.0;
			}
			// Calculate the time since the last note in the scratch lane on the same side,
			// and use that to calculate the sigmoid influence on the PDF.
			return sigmoid(
				data[scratchFor(lane)].since(time),
				config.getMurizaraInverseTime(),
				config.getMurizaraOffset()
			);
		}

		private final void applyPDF(long time) {
			for (int i = 0; i < LANES; i++) {
				if (isScratchLane(i)) {
					continue;
				}
				fff[playerIndex(i)].applyPDF(normalize(i), pdfJack(i, time) * pdfMurizara(i, time));
			}
		}

		private final void removeLane(int lane) {
			fff[playerIndex(lane)].removeLane(lane);
		}

		private final void prepareState() {
			fff[0].initialize();
			fff[1].initialize();
		}

		public void protectLN() {
			for (int i = 0; i < LANES; i++) {
				if (data[i].head != null) {
					// Make sure the lane appears as a value in the permuter without assigning it a legal key.
					Logger.getGlobal().info("Protecting LN: " + i);
					permuter.put(1000+i, i);
					removeLane(i);
				}
			}
		}

		public void resolveLN(TimeLine tl) {
			Note[] notes = new Note[LANES];
			Note[] hnotes = new Note[LANES];
			for (int i = 0; i < LANES; i++) {
				notes[i] = tl.getNote(i);
				hnotes[i] = tl.getHiddenNote(i);
			}

			for (int i = 0; i < LANES; i++) {
				if (notes[i] instanceof LongNote && ((LongNote) notes[i]).isEnd()) {
					for (int laneHead = 0; laneHead < LANES; laneHead++) {
						if (data[laneHead].head == ((LongNote) notes[i]).getPair()) {
							data[laneHead].head = null;
							Logger.getGlobal().info("Resolving LN: " + i + " -> " + laneHead);
							permuter.put(i, laneHead);
							removeLane(laneHead);
							break;
						}
					}
				}
			}
		}

		private EnumSet<FourteenizerRegion> placeBSS(int lane, long time) {
			// If either scratch lane currently contains an active BSS,
			// map this LN to a key lane instead.
			if (data[scratchFor(lane)].head != null) {
				return EnumSet.of(FourteenizerRegion.P1_KEY, FourteenizerRegion.P2_KEY);
			}
			// If both sides currently contain an active key LN,
			// map this LN to a key lane instead.
			boolean hasActiveLN[] = {false, false};
			int murizaraProtections[] = {0, 0};
			for (int i = 0; i < LANES; i++) {
				if (isScratchLane(i)) {
					continue;
				}
				if (data[i].head != null) {
					hasActiveLN[playerIndex(i)] = true;
				}
				murizaraProtections[playerIndex(i)] += (
					rand.nextDouble() > pdfMurizara(i, time)
				) ? 1 : 0;
			}
			if (hasActiveLN[0] && hasActiveLN[1]) {
				return EnumSet.of(FourteenizerRegion.P1_KEY, FourteenizerRegion.P2_KEY);
			}
			// Otherwise, map to the TT lane on the side
			// that triggers fewer murizara preventions.
			EnumSet<FourteenizerRegion> result = EnumSet.noneOf(FourteenizerRegion.class);
			if (murizaraProtections[0] <= murizaraProtections[1]) {
				result.add(FourteenizerRegion.P1_TT);
			}
			if (murizaraProtections[1] <= murizaraProtections[0]) {
				result.add(FourteenizerRegion.P2_TT);
			}
			return result;
		}

		private EnumSet<FourteenizerRegion> placeKeyLN(int lane, long time) {
			// If either side currently contains an active key LN,
			// choose a side that has at least one active key LN.
			boolean hasActiveLN[] = {false, false};
			int protections[] = {0, 0};
			for (int i = 0; i < LANES; i++) {
				if (isScratchLane(i)) {
					continue;
				}
				if (data[i].head != null) {
					hasActiveLN[playerIndex(i)] = true;
				}
				// protections[playerIndex(i)] += (
				// 	rand.nextDouble() > pdfJack(i, time)
				// ) ? 1 : 0;
				protections[playerIndex(i)] += (
					rand.nextDouble() > pdfMurizara(i, time)
				) ? 1 : 0;
			}
			if (hasActiveLN[0] || hasActiveLN[1]) {
				EnumSet<FourteenizerRegion> result = EnumSet.noneOf(FourteenizerRegion.class);
				if (hasActiveLN[0]) {
					result.add(FourteenizerRegion.P1_KEY);
				}
				if (hasActiveLN[1]) {
					result.add(FourteenizerRegion.P2_KEY);
				}
				return result;
			}
			// Otherwise, choose a side that triggers fewer murizara & jack preventions.
			EnumSet<FourteenizerRegion> result = EnumSet.noneOf(FourteenizerRegion.class);
			if (protections[0] <= protections[1]) {
				result.add(FourteenizerRegion.P1_KEY);
			}
			if (protections[1] <= protections[0]) {
				result.add(FourteenizerRegion.P2_KEY);
			}
			return result;
		}

		private EnumSet<FourteenizerRegion> placeTT(int lane, long time) {
			EnumSet<FourteenizerRegion> result = EnumSet.of(
				FourteenizerRegion.P1_TT, FourteenizerRegion.P2_TT
			);
			// If there is a currently active BSS,
			// remove that side from the pool of selectable lanes.
			if (data[scratchFor(7)].head != null) {
				result.remove(FourteenizerRegion.P1_TT);
			}
			if (data[scratchFor(15)].head != null) {
				result.remove(FourteenizerRegion.P2_TT);
			}
			// If all available sides currently contain an active key LN,
			// map this SN to a key lane instead.
			boolean hasActiveLN[] = {false, false};
			int murizaraProtections[] = {0, 0};
			for (int i = 0; i < LANES; i++) {
				if (isScratchLane(i)) {
					continue;
				}
				if (data[i].head != null) {
					hasActiveLN[playerIndex(i)] = true;
				}
				murizaraProtections[playerIndex(i)] += (
					rand.nextDouble() > pdfMurizara(i, time)
				) ? 1 : 0;
			}
			if (hasActiveLN[0]) {
				result.remove(FourteenizerRegion.P1_TT);
			}
			if (hasActiveLN[1]) {
				result.remove(FourteenizerRegion.P2_TT);
			}
			if (result.isEmpty()) {
				return EnumSet.of(FourteenizerRegion.P1_KEY, FourteenizerRegion.P2_KEY);
			}
			if (result.size() == 1) {
				return result;
			}
			// Otherwise, map to the TT lane on the side
			// that triggers fewer murizara preventions.
			result = EnumSet.noneOf(FourteenizerRegion.class);
			if (murizaraProtections[0] <= murizaraProtections[1]) {
				result.add(FourteenizerRegion.P1_TT);
			}
			if (murizaraProtections[1] <= murizaraProtections[0]) {
				result.add(FourteenizerRegion.P2_TT);
			}
			return result;
		}

		private EnumSet<FourteenizerRegion> placeKeySN(int lane, long time) {
			EnumSet<FourteenizerRegion> result = EnumSet.of(
				FourteenizerRegion.P1_KEY, FourteenizerRegion.P2_KEY
			);
			// If there is a currently active BSS,
			// remove that side from the pool of selectable lanes.
			if (data[scratchFor(7)].head != null) {
				result.remove(FourteenizerRegion.P1_KEY);
			}
			if (data[scratchFor(15)].head != null) {
				result.remove(FourteenizerRegion.P2_KEY);
			}
			if (result.size() <= 1) {
				return result;
			}
			// If there is a currently active key LN,
			// choose an available side that triggers fewer LN avoidances.
			int avoidLNs[] = {0, 0};
			int protections[] = {0, 0};
			for (int i = 0; i < LANES; i++) {
				if (isScratchLane(i)) {
					continue;
				}
				if (data[i].head != null) {
					avoidLNs[playerIndex(i)]++;
				}
				// protections[playerIndex(i)] += (
				// 	rand.nextDouble() > pdfJack(i, time)
				// ) ? 1 : 0;
				protections[playerIndex(i)] += (
					rand.nextDouble() > pdfMurizara(i, time)
				) ? 1 : 0;
			}
			if (avoidLNs[0] + avoidLNs[1] > 0) {
				boolean testP1 = rand.nextDouble() < Math.exp(-avoidLNs[0]*config.getAvoidLNFactor());
				boolean testP2 = rand.nextDouble() < Math.exp(-avoidLNs[1]*config.getAvoidLNFactor());
				if (!testP1) {
					result.remove(FourteenizerRegion.P1_KEY);
				}
				if (!testP2) {
					result.remove(FourteenizerRegion.P2_KEY);
				}
				return result;
			}
			// Otherwise, choose a side that triggers fewer murizara & jack preventions.
			result = EnumSet.noneOf(FourteenizerRegion.class);
			if (protections[0] <= protections[1]) {
				result.add(FourteenizerRegion.P1_KEY);
			}
			if (protections[1] <= protections[0]) {
				result.add(FourteenizerRegion.P2_KEY);
			}
			return result;
		}

		private void allocate(TimeLine tl, boolean mapScratchToKey) {
			allocation.clear();
			for (int i = 0; i < LANES; i++) {
				if (permuter.containsKey(i)) {
					// Already have a mapping for this source note.
					continue;
				}

				Note note = tl.getNote(i);
				Note hnote = tl.getHiddenNote(i);

				if (note == null && hnote == null) {
					// No notes here.
					continue;
				}
				if (note instanceof LongNote) {
					if (((LongNote) note).isEnd()) {
						// LN ends are already taken care of by resolveLN().
						continue;
					}
					// BSS or LN head.
					if (isScratchLane(i) && !mapScratchToKey) {
						allocation.put(i, placeBSS(i, tl.getTime()));
					} else {
						allocation.put(i, placeKeyLN(i, tl.getTime()));
					}
					continue;
				}
				// Short note (or hidden note).
				if (isScratchLane(i) && !mapScratchToKey) {
					allocation.put(i, placeTT(i, tl.getTime()));
				} else {
					allocation.put(i, placeKeySN(i, tl.getTime()));
				}
			}
		}

		private boolean reallocateScratch() {
			int allocationCount[] = {0, 0};
			for (Map.Entry<Integer, EnumSet<FourteenizerRegion>> entry : allocation.entrySet()) {
				int lane = entry.getKey();
				EnumSet<FourteenizerRegion> regions = entry.getValue();
				if (regions.contains(FourteenizerRegion.P1_KEY) && regions.size() == 1) {
					allocationCount[0]++;
				}
				if (regions.contains(FourteenizerRegion.P2_KEY) && regions.size() == 1) {
					allocationCount[1]++;
				}
			}
			if (allocationCount[0] > config.getScratchReallocationThreshold()) {
				return true;
			}
			if (allocationCount[1] > config.getScratchReallocationThreshold()) {
				return true;
			}
			return false;
		}

		private boolean mapNoteRAN(TimeLine tl, int lane) {
			Note note = tl.getNote(lane);
			if (note == null) {
				// No notes here. (Don't care about hidden notes.)
				return false;
			}
			if (permuter.containsKey(lane)) {
				// Already have a mapping for this source note.
				return false;
			}
			if (!allocation.containsKey(lane)) {
				// Don't have an allocation for this source note.
				return false;
			}

			long time = tl.getTime();
			// Filter the key note history to:
			// - notes in selectable lanes
			//   - selectable sides for that note
			//   - at least one five-finger favorability combo matches on that side
			// - that were mapped without H-RAN
			// - and whose source lane matches the incoming note's source lane
			EnumSet<FourteenizerRegion> selectableRegions = allocation.get(lane);
			List<Integer> filteredHistory = new ArrayList<>();
			for (int i = 0; i < LANES; i++) {
				if (data[i].note == null) {
					// No history for this lane.
					continue;
				}
				if (data[i].head != null) {
					// Active LN, can't place here.
					continue;
				}
				if (data[i].hran) {
					// Last note was mapped with H-RAN, can't follow with RAN.
					continue;
				}
				if (!selectableRegions.contains(l2r(i))) {
					// Not a selectable region for this note.
					continue;
				}
				if (!fff[playerIndex(i)].hasMatching(normalize(i))) {
					// No matching five-finger favorability combo.
					continue;
				}
				// if (lane != data[i].source) {
				// 	// Source lane doesn't match.
				// 	continue;
				// }
				// Passes all filters.
				filteredHistory.add(i);
			}
			Collections.shuffle(filteredHistory, rand);
			// Run the RAN vs. H-RAN trigger for each note in that filtered history.
			// If on any note, the trigger doesn't fire:
			// - use the same mapping as that previous note
			// - remove that lane from the selectable lanes
			// - continue to the next incoming note
			for (int i : filteredHistory) {
				final double ld = levenshteinDistance(data[i].note, note);
				if (rand.nextDouble() * ld < sigmoid(
					data[i].since(time),
					config.getHranInverseTime(),
					config.getHranOffset()
				)) {
					permuter.put(lane, i);
					fff[playerIndex(i)].removeLane(normalize(i));
					return true;
				}
			}
			return false;
		}

		private boolean mapNoteHRAN(TimeLine tl, int lane) {
			if (tl.getNote(lane) == null && tl.getHiddenNote(lane) == null) {
				// No notes here.
				return false;
			}
			if (permuter.containsKey(lane)) {
				// Already have a mapping for this source note.
				return false;
			}
			if (!allocation.containsKey(lane)) {
				// Don't have an allocation for this source note.
				return false;
			}

			// Choose among the selectable lanes using the flattened
			// five-finger favorability PDF across selectable sides for that note.
			EnumSet<FourteenizerRegion> selectableRegions = allocation.get(lane);
			double pdf[] = new double[LANES];
			for (int i = 0; i < LANES; i++) {
				if (isScratchLane(i)) {
					continue;
				}
				if (permuter.containsValue(i)) {
					continue;
				}
				if (!selectableRegions.contains(l2r(i))) {
					continue;
				}
				pdf[i] = fff[playerIndex(i)].sumPDF(i);
			}
			Logger.getGlobal().info("PDF: " + Arrays.toString(pdf));
			double sum = Arrays.stream(pdf).sum();
			if (sum <= 0.0) {
				return false;
			}
			double r = rand.nextDouble();
			for (int i = 0; i < LANES; i++) {
				r -= pdf[i] / sum;
				if (r <= 0.0) {
					permuter.put(lane, i);
					fff[playerIndex(i)].removeLane(normalize(i));
					return true;
				}
			}
			return false;
		}

		private boolean mapScratch() {
			// Should be only one scratch note per timeline...
			for (Map.Entry<Integer, EnumSet<FourteenizerRegion>> entry : allocation.entrySet()) {
				int laneFrom = entry.getKey();
				EnumSet<FourteenizerRegion> regions = entry.getValue();
				if (isScratchLane(laneFrom)) {
					FourteenizerRegion chosen = new ArrayList<>(regions).get(rand.nextInt(regions.size()));
					int laneTo = (chosen == FourteenizerRegion.P1_TT) ? 7 : 15;
					permuter.put(laneFrom, laneTo);
					return true;
				}
			}
			return true;
		}

		private boolean mapKeys(TimeLine tl) {
			// Build a set of lanes with incoming notes.
			Set<Integer> hasNote = new HashSet<>();
			for (int i = 0; i < LANES; i++) {
				if (tl.getNote(i) == null && tl.getHiddenNote(i) == null) {
					continue;
				}
				if (permuter.containsKey(i)) {
					continue;
				}
				hasNote.add(i);
			}
			Set<Integer> remaining = new HashSet<>();
			for (int i : hasNote) {
				if (!mapNoteRAN(tl, i)) {
					remaining.add(i);
				}
			}
			for (int i : remaining) {
				if (!mapNoteHRAN(tl, i)) {
					return false;
				}
			}
			return true;
		}

		public void performPermutation(TimeLine tl) {
			Logger.getGlobal().info("Performing permutation: " + permuter);

			Note[] notes = new Note[LANES];
			Note[] hnotes = new Note[LANES];
			for (int i = 0; i < LANES; i++) {
				notes[i] = tl.getNote(i);
				hnotes[i] = tl.getHiddenNote(i);
				tl.setNote(i, null);
				tl.setHiddenNote(i, null);
			}
			
			for (int i = 0; i < LANES; i++) {
				int mapped = permuter.getOrDefault(i, i);
				if (notes[i] != null || hnotes[i] != null) {
					Logger.getGlobal().info("Setting note: " + i + " -> " + mapped);
					tl.setNote(mapped, notes[i]);
					tl.setHiddenNote(mapped, hnotes[i]);
					data[mapped].source = i;
				}
			}

			permuter.clear();
		}

		public void updateState(TimeLine tl) {
			if (!tl.existNote() && !tl.existHiddenNote()) {
				return;
			}

			Note[] notes = new Note[LANES];
			Note[] hnotes = new Note[LANES];
			for (int i = 0; i < LANES; i++) {
				notes[i] = tl.getNote(i);
				hnotes[i] = tl.getHiddenNote(i);
			}

			for (int i = 0; i < LANES; i++) {
				if (notes[i] != null) {
					data[i].note = notes[i];
				}
				if (notes[i] instanceof LongNote && !((LongNote) notes[i]).isEnd()) {
					data[i].head = (LongNote) notes[i];
				}
			}
			count++;
		}

		public void process(TimeLine tl) {
			Logger.getGlobal().info("Processing TL: " + tl.getTime());

			// Prepare state machine for this round of updates.
			prepareState();

			// Handle active LN.
			protectLN();
			resolveLN(tl);

			// Attempt to allocate notes.
			allocate(tl, false);
			if (reallocateScratch()) {
				allocate(tl, true);
			}

			// Map the scratch.
			mapScratch();

			// Apply PDF to the key lanes to set up the five-finger favorability.
			applyPDF(tl.getTime());
			Logger.getGlobal().info("PDF: " + fff[0].fff + " " + fff[1].fff);

			// Map the keys.
			mapKeys(tl);
			Logger.getGlobal().info("Permuter: " + permuter);

			// Actually perform the permutation.
			performPermutation(tl);
			updateState(tl);
		}
	}

	public static class PlayerFourteenizer extends LaneShuffleModifier {
		private PlayerConfig config;
		
		public PlayerFourteenizer(PlayerConfig config) {
			super(0, true, false);
			setAssistLevel(AssistLevel.ASSIST);
			this.config = config;
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key * 2).toArray();
			return result;
		}

		@Override
		public void modify(BMSModel model) {
			if (model.getMode().player == 1) {
				return;
			}

			Mode mode = model.getMode();
			final int[] keys = getKeys(mode, player, isScratchLaneModify);
			if(keys.length == 0) {
				return;
			}
			FourteenizerState stateMachine = new FourteenizerState(getSeed(), config, model);

			TimeLine[] timelines = model.getAllTimeLines();
			for (int index = 0; index < timelines.length; index++) {
				TimeLine tl = timelines[index];
				if (tl.existNote() || tl.existHiddenNote()) {
					stateMachine.process(tl);
				}
			}
		}
	}

	public static class LaneCrossShuffleModifier extends LaneShuffleModifier {

		public LaneCrossShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(AssistLevel.LIGHT_ASSIST);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			int[] result = IntStream.range(0, model.getMode().key).toArray();
			for (int i = 0; i < keys.length / 2 - 1; i += 2) {
				result[keys[i]] = keys[i + 1];
				result[keys[i + 1]] = keys[i];
				result[keys[keys.length - i - 1]] = keys[keys.length - i - 2];
				result[keys[keys.length - i - 2]] = keys[keys.length - i - 1];
			}
			return result;
		}
	}

	public static class LanePlayableRandomShuffleModifier extends LaneShuffleModifier {

		public LanePlayableRandomShuffleModifier(int player, boolean isScratchLaneModify) {
			super(player, isScratchLaneModify, true);
			setAssistLevel(AssistLevel.LIGHT_ASSIST);
		}

		protected int[] makeRandom(int[] keys, BMSModel model) {
			// 無理押しが来ないようにLaneShuffleをかける(ただし正規鏡を除く)。無理押しが来ない譜面が存在しない場合は正規か鏡でランダム
			Mode mode = model.getMode();
			int lanes = mode.key;
			int[] ln = new int[lanes];
			int[] endLnNoteTime = new int[lanes];
			int max = 0;
			for (int key : keys) {
				max = Math.max(max, key);
			}
			boolean isImpossible = false; //7個押し以上が存在するかどうか
			Set<Integer> originalPatternList = new HashSet<>(); //3個押し以上の同時押しパターンのセット
			Arrays.fill(ln, -1);
			Arrays.fill(endLnNoteTime, -1);

			//3個押し以上の同時押しパターンのリストを作る
			for (TimeLine tl : model.getAllTimeLines()) {
				if (tl.existNote()) {
					//LN
					for (int i = 0; i < lanes; i++) {
						Note n = tl.getNote(i);
						if (n instanceof LongNote ln2) {
							if (ln2.isEnd() && tl.getTime() == endLnNoteTime[i]) {
								ln[i] = -1;
								endLnNoteTime[i] = -1;
							} else {
								ln[i] = i;
								if (!ln2.isEnd()) {
									endLnNoteTime[i] = ln2.getPair().getTime();
								}
							}
						}
					}
					//通常ノート
					List<Integer> noteLane = new ArrayList<>(keys.length);
					for (int i = 0; i < lanes; i++) {
						Note n = tl.getNote(i);
						if (n != null && n instanceof NormalNote || ln[i] != -1) {
							noteLane.add(i);
						}
					}
					//7個押し以上が一つでも存在すれば無理押しが来ない譜面は存在しない
					if (noteLane.size() >= 7) {
						isImpossible = true;
						break;
					} else if (noteLane.size() >= 3) {
						int pattern = 0;
						for (Integer i : noteLane) {
							pattern += (int) Math.pow(2, i);
						}
						originalPatternList.add(pattern);
					}
				}
			}

			List<List<Integer>> kouhoPatternList = new ArrayList<>(); //無理押しが来ない譜面のリスト
			if (!isImpossible) {
				kouhoPatternList = searchForNoMurioshiLaneCombinations(originalPatternList, keys);
			}

			Logger.getGlobal().info("無理押し無し譜面数 : "+(kouhoPatternList.size()));

			int[] result = new int[9];
			if (kouhoPatternList.size() > 0) {
				int r = (int) (Math.random() * kouhoPatternList.size());
				for (int i = 0; i < 9; i++) {
					result[kouhoPatternList.get(r).get(i)] = i;
				}
			//無理押しが来ない譜面が存在しない場合は正規か鏡でランダム
			} else {
				int mirror = (int) (Math.random() * 2);
				for (int i = 0; i < 9; i++) {
					result[i] = mirror == 0 ? i : 8 - i;
				}
			}
			return result;
		}

		private List<List<Integer>> searchForNoMurioshiLaneCombinations(Set<Integer> originalPatternList, int[] keys) {
			List<List<Integer>> noMurioshiLaneCombinations = new ArrayList<>(); // 無理押しが来ない譜面のリスト
			List<Integer> tempPattern = new ArrayList<>(keys.length);
			int[] indexes = new int[9];
			int[] laneNumbers = new int[9];
			for (int i = 0; i < 9; i++) {
				laneNumbers[i] = i;
				indexes[i] = 0;
			}

			List<List<Integer>> murioshiChords = Arrays.asList(
					Arrays.asList(1, 4, 7),
					Arrays.asList(1, 4, 8),
					Arrays.asList(1, 4, 9),
					Arrays.asList(1, 5, 8),
					Arrays.asList(1, 5, 9),
					Arrays.asList(1, 6, 9),
					Arrays.asList(2, 5, 8),
					Arrays.asList(2, 5, 9),
					Arrays.asList(2, 6, 9),
					Arrays.asList(3, 6, 9)
			);

			int i = 0;
			while (i < 9) {
				if (indexes[i] < i) {
					swap(laneNumbers, i % 2 == 0 ? 0 : indexes[i], i);

					boolean murioshiFlag = false;
					for (Integer pattern : originalPatternList) {
						tempPattern.clear();
						for (int j = 0; j < 9; j++) {
							if (((int) (pattern / Math.pow(2, j)) % 2) == 1) {
								tempPattern.add(laneNumbers[j] + 1);
							}
						}

						murioshiFlag = murioshiChords.stream().anyMatch(tempPattern::containsAll);
						if (murioshiFlag) {
							break;
						}
					}
					if (!murioshiFlag) {
						List<Integer> randomCombination = new ArrayList<>();
						for (int j = 0; j < 9; j++) {
							randomCombination.add(laneNumbers[j]);
						}
						noMurioshiLaneCombinations.add(randomCombination);
					}

					indexes[i]++;
					i = 0;
				} else {
					indexes[i] = 0;
					i++;
				}
			}

			noMurioshiLaneCombinations.remove(Arrays.asList(8, 7, 6, 5, 4, 3, 2, 1, 0));
			return noMurioshiLaneCombinations;
		}

		private void swap(int[] input, int a, int b) {
			int tmp = input[a];
			input[a] = input[b];
			input[b] = tmp;
		}
	}
}

