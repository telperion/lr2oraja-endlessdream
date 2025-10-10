package bms.player.beatoraja.pattern;

import bms.model.*;

import java.util.*;

import bms.player.beatoraja.modmenu.RandomTrainer;

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

	public enum FourteenizerAlgorithm {
		NONE,
		A,
		B,
		C,
	}

	public class FourteenizerState {
		private static final int LANES = 16;
		private Note[] noteHistory;
		private LongNote[] heads;
		private Map<Integer, Integer> permuter;
		private java.util.Random rand;
		private int count;


		private final boolean isScratchLane(int lane) {
			return lane == 7 || lane == 15;
		}

		// (1 - e^(-kt)) constants for the pdf calculation

		// Time since the last note in the same lane
		// Applied on key lanes only
		private final double AVOID_JACKS = 0.5;
		private final boolean impactJacks(int laneTarget, int laneTest) {
			return (laneTarget == laneTest);
		}

		// Time since the last note in the scratch lane
		// Applied on key lanes only
		private final double AVOID_MURIZARA = 0.2;
		private final boolean impactMurizara(int laneTarget, int laneTest) {
			if (laneTarget <= 7) {
				// Check P1 turntable.
				return (laneTest == 7);
			}
			// Check P2 turntable.
			return (laneTest == 15);
		}

		// Constant weight applied if the test lane is currently occupied by LN
		// and on the same side as the target lane
		// AVOID_LN_DODGE ~ 0 -> LN will have no effect on other notes
		// Applied on all lanes
		private final double AVOID_LN_DODGE = 0.8;
		private final boolean impactLNDodge(int laneTarget, int laneTest) {
			if ((laneTarget <= 7) != (laneTest <= 7)) {
				return false;
			}
			if (heads[laneTest] != null) {
				return true;
			}
			return false;
		}

		// Time since the last note in the other 56 lane on the same side
		// Applied on "56" lanes only (indices 2, 3, 12, 13)
		private final double AVOID_56 = 0.1;
		private final boolean impact56(int laneTarget, int laneTest) {
			if (laneTarget == 1) {return (laneTest == 2);}
			if (laneTarget == 2) {return (laneTest == 1);}
			if (laneTarget == 12) {return (laneTest == 13);}
			if (laneTarget == 13) {return (laneTest == 12);}
			return false;
		}

		// Time since the last note in adjacent lanes on the same side
		// Applied on key lanes only
		private final double AVOID_PILL = 5.0;
		private final double AVOID_PILL_MIN_TIME = 0.1;
		private final boolean impactPill(int laneTarget, int laneTest) {
			if (isScratchLane(laneTest)) {
				// Scratch lane cannot form a pill chord.
				return false;
			}
			if ((laneTarget <= 7) != (laneTest <= 7)) {
				// Don't compare across sides.
				return false;
			}
			return (laneTarget - laneTest) == -1 || (laneTarget - laneTest) == 1;
		}

		// Scaling applied to the avoid murizara constant
		// (1 - e^(-kt)*e^(-kl)), l = lanes between scratch lane and target lane
		// e.g. lane 14 -> l=0, lane 12 -> l=2
		// DEFINE_MURIZARA ~ 0 -> all lanes are treated equally for murizara
		private final double DEFINE_MURIZARA = 0.0;
		private final int getMurizaraDistance(int laneTarget) {
			if (laneTarget <= 7) {
				return 7 - laneTarget;
			}
			return 15 - laneTarget;
		}

		// Preference for keeping consecutive scratch notes in the same lane
		private final double PREFER_CONSECUTIVE_SCRATCH = 0.5;

		// So you're saying there's a chance.
		private final double MIN_PDF = 1e-24;

		
		public FourteenizerState(long seed) {
			this.noteHistory = new Note[LANES];
			this.heads = new LongNote[LANES];
			this.permuter = new HashMap<>();
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
					dt = 0.0;
				}
				else if (permuter.containsValue(i)) {
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
						final double df = Math.exp(-PREFER_CONSECUTIVE_SCRATCH * dt);
						if (lane != i) {
							pdf *= df;
						}
						// Logger.getGlobal().info("Scratch lane: " + lane + " -> " + i + " (dt: " + dt + ", df: " + df + ")");
					}
					else {
						pdf = 0.0;
					}
				}
				else {
					// Incorporate avoidance rules
					if (impactJacks(lane, i)) {
						final double mdf = (1.0 - Math.exp(-AVOID_JACKS * dt));
						pdf *= mdf;
						// Logger.getGlobal().info("Jack: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
					if (impactMurizara(lane, i)) {
						final double mdf = (1.0 - Math.exp(-AVOID_MURIZARA * dt) * Math.exp(-DEFINE_MURIZARA * getMurizaraDistance(i)));
						pdf *= mdf;
						// Logger.getGlobal().info("Murizara: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
					if (impact56(lane, i)) {
						final double mdf = (1.0 - Math.exp(-AVOID_56 * dt));
						pdf *= mdf;
						// Logger.getGlobal().info("56: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
					if (impactPill(lane, i)) {
						final double mdf = (1.0 - Math.exp(-AVOID_PILL * Math.max(dt, AVOID_PILL_MIN_TIME)));
						pdf *= mdf;
						// Logger.getGlobal().info("Knight: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
					if (impactLNDodge(lane, i)) {
						final double mdf = 1.0 - AVOID_LN_DODGE;
						pdf *= mdf;
						// Logger.getGlobal().info("LNDodge: " + lane + " -> " + i + " (mdf: " + mdf + ")");
					}
				}
			}
			return Math.max(pdf, MIN_PDF);
		}

		public void protectLN() {
			for (int i = 0; i < LANES; i++) {
				if (heads[i] != null) {
					// Make sure the lane appears as a value in the permuter without assigning it a legal key.
					Logger.getGlobal().info("Protecting LN: " + i);
					permuter.put(1000+i, i);
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
							break;
						}
					}
				}
			}
		}

		public boolean randomizeSpecificLanes(Set<Integer> lanesFrom, long time) {
			if (lanesFrom.size() == 0) {
				return true;
			}

			// What lanes are up for consideration?
			boolean incorporateKeyLanes = !lanesFrom.stream().allMatch(this::isScratchLane);
			boolean incorporateScratchLanes = lanesFrom.stream().anyMatch(this::isScratchLane);

			// Calculate PDF list
			List<Double> pdfList = new ArrayList<>();
			List<Integer> pdfLanes = new ArrayList<>();
			for (int i = 0; i < LANES; i++) {
				pdfLanes.add(i);
				if (permuter.containsValue(i)) {
					pdfList.add(0.0);
					continue;
				}
				if (!incorporateScratchLanes && isScratchLane(i)) {
					pdfList.add(0.0);
					continue;
				}
				if (!incorporateKeyLanes && !isScratchLane(i)) {
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
			protectLN();
			resolveLN(tl);

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

			Set<Integer> scratchLanes = new HashSet<>(Set.of(7, 15));
			scratchLanes.retainAll(hasNote);
			randomizeSpecificLanes(scratchLanes, tl.getTime());

			Set<Integer> keyLanes = new HashSet<>(Set.of(0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 11, 12, 13, 14));
			keyLanes.retainAll(hasNote);
			for (int lane : keyLanes) {
				randomizeSpecificLanes(Set.of(lane), tl.getTime());
			}

			performPermutation(tl);
			updateState(tl);
		}
	}

	public static class PlayerFourteenizer extends LaneShuffleModifier {
		private FourteenizerAlgorithm algorithm = FourteenizerAlgorithm.NONE;
		public FourteenizerAlgorithm getAlgorithm() {return this.algorithm;}

		public PlayerFourteenizer(FourteenizerAlgorithm algo) {
			super(0, true, false);
			setAssistLevel(AssistLevel.ASSIST);
			this.algorithm = algo;
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
			FourteenizerState stateMachine = new FourteenizerState(getSeed());

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

