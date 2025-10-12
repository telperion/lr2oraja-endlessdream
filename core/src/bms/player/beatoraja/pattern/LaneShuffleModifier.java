package bms.player.beatoraja.pattern;

import bms.model.*;

import java.util.*;

import bms.player.beatoraja.modmenu.RandomTrainer;
import bms.player.beatoraja.PlayerConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.stream.IntStream;

import com.badlogic.gdx.utils.IntArray;

/**
 * レーン単位でノーツを入れ替えるオプション
 *
 * @author exch
 */
public abstract class LaneShuffleModifier extends PatternModifier {
	private static final Logger logger = LoggerFactory.getLogger(LaneShuffleModifier.class);

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

	public double l2pref(int lane) {
		if (lane <= 7) {
			return 1.0 - (lane / 6.0);
		}
		return (lane - 8) / 6.0;
	}

	public class FourteenizerState {
		private PlayerConfig config;
		private static final int LANES = 16;
		private Note[] noteHistory;
		private LongNote[] heads;
		private Map<Integer, Double>[] fiveFingerFavorability;
		private Map<Integer, Integer> permuter;
		private java.util.Random rand;
		private int count;
		
		private final double sigmoid(double x, double tightness, double offset) {
			final double neg = Math.exp(-tightness *  x);
			final double pos = Math.exp( tightness * (x - 2.0*offset));
			return 0.5 * (pos - neg) / (pos + neg) + 0.5;
		}

		private final void fffInitialize(int player) {
			Map<Integer, Double> fff = new HashMap<>();
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
			fiveFingerFavorability[player] = fff;
		}

		private final void fffRemoveOccupiedLane(int lane) {
			final int player = (lane > 7) ? 1 : 0;
			final int laneSide = (lane > 7) ? 14 - lane : lane;

			// Remove this lane from all keys in the five-finger favorability map.
			// Any keys reduced to 0 (no lanes) are removed from the map entirely.
			Map<Integer, Double> fff = fiveFingerFavorability[player].entrySet().stream().map(entry -> {
				final int key = entry.getKey();
				final int bit = 1 << laneSide;	
				if ((key & bit) == 0) {
					// If this arrangement doesn't have the lane, we can't use it.
					return null;
				}
				if ((key & ~bit) == 0) {
					// If all this arrangement has left is the lane, we can't use it.
					return null;
				}
				return Map.entry(key, entry.getValue());
			}).filter(entry -> entry != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			fiveFingerFavorability[player] = fff;
		}

		private final void fffApplyPDF(int lane, double pdf) {
			final int player = (lane > 7) ? 1 : 0;
			final int laneSide = (lane > 7) ? 14 - lane : lane;

			Map<Integer, Double> fff = fiveFingerFavorability[player].entrySet().stream().map(entry -> {
				final int key = entry.getKey();
				final int bit = 1 << laneSide;	
				if ((key & bit) == 0) {
					return entry;
				}
				return Map.entry(key, entry.getValue() * pdf);
			}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			fiveFingerFavorability[player] = fff;
		}

		private final int fffMaxLaneCount(int player) {
			return fiveFingerFavorability[player].entrySet().stream().map(entry -> {
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

		private final Set<Integer> fffSelect(int player, int minCount) {
			Set<Integer> result = new HashSet<>();

			// Filter out FFF options without enough lanes.
			Map<Integer, Double> fff = fiveFingerFavorability[player].entrySet().stream().map(entry -> {
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
			if (fff.isEmpty()) {
				return result;
			}

			// Select from the remaining options based on their favorability.
			double favorSum = fff.values().stream().mapToDouble(Double::doubleValue).sum();
			if (favorSum <= 0.0) {
				return result;
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

			// Expand the bitset into individual lanes.
			for (int i = 0; i < 7; i++) {
				if ((fiveFinger & (1 << i)) != 0) {
					result.add(i);
				}
			}
			return result;
		}


		private final boolean isScratchLane(int lane) {
			return l2r(lane) == FourteenizerRegion.P1_TT || l2r(lane) == FourteenizerRegion.P2_TT;
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

		
		public FourteenizerState(long seed, PlayerConfig config) {
			this.config = config;
			this.noteHistory = new Note[LANES];
			this.heads = new LongNote[LANES];
			this.permuter = new HashMap<>();
			this.fiveFingerFavorability = new Map[2];
			this.fiveFingerFavorability[0] = new HashMap<>();
			this.fiveFingerFavorability[1] = new HashMap<>();
			this.fffInitialize(0);
			this.fffInitialize(1);
			this.rand = new java.util.Random(seed);
			this.count = 0;
		}

		private double pdf(int lane, long time) {
			if (heads[lane] != null) {
				// Early exit - can't put a new note in the middle of a LN.
				return 0.0;
			}

			if (permuter.containsValue(lane)) {
				// Early exit - already receiving a note this update.
				return 0.0;
			}
			
			boolean anyHistory = permuter.size() > 0;
			for (int i = 0; !anyHistory && i < LANES; i++) {
				if (noteHistory[i] != null) {
					anyHistory = true;
					break;
				}
			}
			if (!anyHistory) {
				// Early exit - no note history to influence positioning yet.
				return 1.0;
			}
			
			double pdf = 1.0;
			for (int i = 0; i < LANES; i++) {
				// Calculate the time since the last note in the test lane.
				double dt = 0.0;
				if (heads[i] != null) {
					// Should already be resolved by FFF filtering.
					dt = 0.0;
				}
				else if (permuter.containsValue(i)) {
					// Should already be resolved by FFF filtering.
					dt = 0.0;
				}
				else if (noteHistory[i] != null) {
					dt = time - noteHistory[i].getTime();
				}
				else {
					dt = time;
				}
				dt /= 1000.0;

				if (isScratchLane(lane)) {
					// Special behavior for consecutive scratch.
					if (isScratchLane(i)) {
						final double df = sigmoid(dt, config.getCSTightness(), config.getCSOffset());
						if (lane != i) {
							Logger.getGlobal().info("Consecutive scratch: " + lane + " -> " + i + " (dt: " + dt + ", df: " + df + ")");
							pdf *= df;
						}
						// Logger.getGlobal().info("Scratch lane: " + lane + " -> " + i + " (dt: " + dt + ", df: " + df + ")");
					}
					// Incorporate murizara
					if (impactMurizara(i, lane)) {
						final double mdf = sigmoid(dt, config.getMurizaraTightness(), config.getMurizaraOffset());
						pdf *= mdf;
						// Logger.getGlobal().info("Murizara: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
				}
				else {
					// Incorporate local key repetition into five-finger favorability
					if (impactJacks(lane, i)) {
						final double mdf = sigmoid(dt, config.getJacksTightness(), config.getJacksOffset());
						pdf *= mdf;
						// Logger.getGlobal().info("Jack: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
					// Incorporate murizara
					if (impactMurizara(lane, i)) {
						final double mdf = sigmoid(dt, config.getMurizaraTightness(), config.getMurizaraOffset());
						pdf *= mdf;
						// Logger.getGlobal().info("Murizara: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
				}

			}
			return Math.max(pdf, MIN_PDF);
		}


		public boolean lastScratchWasOnP2() {
			if (permuter.containsValue(7)) {
				return false;
			}
			if (permuter.containsValue(15)) {
				return true;
			}

			if (noteHistory[7] == null) {
				return true;
			}
			if (noteHistory[15] == null) {
				return false;
			}

			if (noteHistory[7].getTime() > noteHistory[15].getTime()) {
				return false;
			}
			return true;
		}

		public void protectLN() {
			for (int i = 0; i < LANES; i++) {
				if (heads[i] != null) {
					// Make sure the lane appears as a value in the permuter without assigning it a legal key.
					Logger.getGlobal().info("Protecting LN: " + i);
					permuter.put(1000+i, i);
					fffRemoveOccupiedLane(i);
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
						if (heads[laneHead] == ((LongNote) notes[i]).getPair()) {
							heads[laneHead] = null;
							Logger.getGlobal().info("Resolving LN: " + i + " -> " + laneHead);
							permuter.put(i, laneHead);
							fffRemoveOccupiedLane(laneHead);
							break;
						}
					}
				}
			}
		}

		public void applyPDF(long time) {
			for (int i = 0; i < LANES; i++) {
				if (!isScratchLane(i)) {
					fffApplyPDF(i, pdf(i, time));
				}
			}
		}

		// Return true if the scratch was mapped successfully to another scratch lane.
		private boolean mapScratch(Set<Integer> lanes, long time) {
			long scratchCount = lanes.stream().filter(this::isScratchLane).count();

			if (scratchCount == 0) {
				return true;
			}

			boolean noMapToP1 = lanes.size() - 1 > fffMaxLaneCount(0);
			boolean noMapToP2 = lanes.size() - 1 > fffMaxLaneCount(1);

			if (noMapToP1 && noMapToP2) {
				return false;
			}
			if (noMapToP1) {
				permuter.put(7, 15);
				permuter.put(15, 15);
				return true;
			}
			if (noMapToP2) {
				permuter.put(7, 7);
				permuter.put(15, 7);
				return true;
			}

			double pdf_P1 = pdf(7, time);
			double pdf_P2 = pdf(15, time);
			double sum = pdf_P1 + pdf_P2;
			// Logger.getGlobal().info("PDF_P1: " + pdf_P1 + ", PDF_P2: " + pdf_P2 + ", Sum: " + sum);
			if (sum <= 0.0) {
				return false;
			}
			if (rand.nextDouble() * sum < pdf_P1) {
				permuter.put(7, 7);
				permuter.put(15, 7);
				return true;
			}
			permuter.put(7, 15);
			permuter.put(15, 15);
			return true;
		}

		private boolean mapKeySubset(Set<Integer> lanesFrom, Set<Integer> lanesTo, long time) {
			// Calculate PDF list
			List<Double> pdfList = new ArrayList<>();
			List<Integer> pdfLanes = new ArrayList<>();
			for (int i = 0; i < LANES; i++) {
				pdfLanes.add(i);
				if (!lanesTo.contains(i)) {
					pdfList.add(0.0);
					continue;
				}
				if (permuter.containsValue(i)) {
					pdfList.add(0.0);
					continue;
				}
				pdfList.add(pdf(i, time));
			}
			Logger.getGlobal().info("PDF list: " + pdfList);

			// Repeatedly select lanes randomly using PDF
			for (int i : lanesFrom) {
				if (permuter.containsKey(i)) {
					Logger.getGlobal().info("Skipping already-mapped lane: " + i + " -> " + permuter.get(i));
					continue;
				}
				if (pdfLanes.size() == 0) {
					Logger.getGlobal().info("No lanes left to map to: " + lanesFrom);
					return false;
				}
				double r = rand.nextDouble();
				final double sum = pdfList.stream().mapToDouble(Double::doubleValue).sum();
				if (sum <= 0.0) {
					Logger.getGlobal().info("PDF excludes further remapping: " + lanesFrom);
					return false;
				}
				for (int index = 0; index < pdfList.size(); index++) {
					r -= pdfList.get(index) / sum;
					if (r <= 0.0) {
						permuter.put(i, pdfLanes.remove(index));
						pdfList.remove(index);
						break;
					}
				}
			}
			return true;
		}

		public boolean mapKeys(Set<Integer> lanesFrom, long time) {
			if (lanesFrom.size() == 0) {
				return true;
			}

			Set<Integer> preferP1 = new HashSet<>();
			Set<Integer> preferP2 = new HashSet<>();
			// Logger.getGlobal().info("Mapping keys: " + lanesFrom + " using permuter: " + permuter);
			for (int lane : lanesFrom) {
				if (permuter.containsValue(7)) {
					preferP2.add(lane);
					continue;
				}
				if (permuter.containsValue(15)) {
					preferP1.add(lane);
					continue;
				}
				if (rand.nextDouble() > sigmoid(
					lastScratchWasOnP2() ? l2pref(lane) : 1.0 - l2pref(lane),
					5.0, 
					0.5
				)) {
					preferP2.add(lane);
					continue;
				}
				preferP1.add(lane);
			}
			int maxP1 = fffMaxLaneCount(0);
			int maxP2 = fffMaxLaneCount(1);
			if (preferP1.size() + preferP2.size() > maxP1 + maxP2) {
				Logger.getGlobal().info("Too many lanes to map: " + lanesFrom + " (preferP1: " + preferP1 + ", preferP2: " + preferP2 + ", maxP1: " + maxP1 + ", maxP2: " + maxP2 + ")");
				return false;
			}
			// Logger.getGlobal().info("PreferP1: " + preferP1 + " (size: " + preferP1.size() + ") vs. maxP1: " + maxP1);
			// Logger.getGlobal().info("PreferP2: " + preferP2 + " (size: " + preferP2.size() + ") vs. maxP2: " + maxP2);
			for (int i = 0; i < preferP1.size() - maxP1; i++) {
				preferP2.add(preferP1.iterator().next());
				preferP1.remove(preferP1.iterator().next());
			}
			for (int i = 0; i < preferP2.size() - maxP2; i++) {
				preferP1.add(preferP2.iterator().next());
				preferP2.remove(preferP2.iterator().next());
			}

			// Pick five-finger arrangements.
			Set<Integer> fffP1 = fffSelect(0, preferP1.size());
			Set<Integer> fffP2 = fffSelect(1, preferP2.size()).stream().map(i -> 14 - i).collect(Collectors.toSet());

			return (
				mapKeySubset(preferP1, fffP1, time) &&
				mapKeySubset(preferP2, fffP2, time)
			);
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
					noteHistory[i] = notes[i];
				}
				if (notes[i] instanceof LongNote && !((LongNote) notes[i]).isEnd()) {
					heads[i] = (LongNote) notes[i];
				}
			}
			count++;
		}

		public void process(TimeLine tl) {
			Logger.getGlobal().info("Processing TL: " + tl.getTime());

			fffInitialize(0);
			fffInitialize(1);
			protectLN();
			resolveLN(tl);
			applyPDF(tl.getTime());

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

			if (mapScratch(hasNote, tl.getTime())) {
				hasNote.removeAll(Set.of(7, 15));
			}
			mapKeys(hasNote, tl.getTime());

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
			FourteenizerState stateMachine = new FourteenizerState(getSeed(), config);

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

			logger.info("無理押し無し譜面数 : {}", kouhoPatternList.size());

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

