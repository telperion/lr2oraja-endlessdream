package bms.player.beatoraja.pattern;

import java.lang.reflect.Array;
import java.sql.Time;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.bytedeco.javacpp.indexer.Index;

import javafx.animation.Timeline;
import javafx.util.Pair;

import bms.model.*;
import elemental2.dom.CSSProperties.MaxHeightUnionType;

public class Fourteenizer {
	public static final String VERSION = "0.1.1";

	public static final double MAX_EXPONENT = 20.0;

    public static enum Input {BGA, KEY, TT}
	public static enum NoteHeld {SN, LN}
    public static enum Side {NONE, P1, P2, BOTH}
	public static enum Strategy {
		FAILURE,		// Note failed to map
		WORST_CASE,		// Emergency fallback to random open key lane
		CONTINUITY,		// Maintain continuity of LN
		SCRATCH,		// TT note mapped to TT lane
		RAN,			// Random that prefers lane consistency
		HRAN			// Random that doesn't care about lane consistency
	}

    public static class Sigmoid {
		// A sigmoid function that runs from a specified minimum asymptote to
		// a maximum asymptote of 1. The distance from the minimum asymptote 
		// at x=0 is the same as the distance from the maximum asymptote at
		// x=inverseTime, and that distance is given by 10^(-adherence).
        //
		//               ^
		//               |
		//          1.0 -+------------------------------------------->
		//               |                            ......---------
		//               |                   ..---````
		//               |                .-`
		// --------------+------ ....-x-`` -------------------------->
		//               |---````        
		// minAsymptote -+------------------------------------------->
        public Double inverseTime;
        public Double adherence;
		public Double asymptote;

        public Sigmoid(Double inverseTime, Double adherence, Double asymptote) {
            this.inverseTime = inverseTime;
            this.adherence = adherence;
            this.asymptote = asymptote;
        }

        private double calculateExpConstant() {
            final double decimality = Math.pow(10.0, -adherence);
            final double asymptoteDiff = (2.0 * decimality) / (1.0 - asymptote) - 1.0;
            return Math.log((1.0 - asymptoteDiff) / (1.0 + asymptoteDiff)) / inverseTime;
        }

        public double evaluate(double x) {
			if (x < 0) {
				return 0.0;
			}
            final double expConstant = calculateExpConstant();
			final double negative_exponent = -expConstant * x;
			final double positive_exponent = expConstant * (x - inverseTime);
			if (positive_exponent > MAX_EXPONENT || negative_exponent < -MAX_EXPONENT) {
				return 1.0;
			}
            final double neg = Math.exp(negative_exponent);
            final double pos = Math.exp(positive_exponent);
            final double result = (0.5 - 0.5 * asymptote) * (pos - neg) / (pos + neg) + (0.5 + 0.5 * asymptote);
			return (result < 0.0) ? 0.0 : result;
        }

        public double evaluateDerivative(double x) {
            if (x < 0) {
                return 0.0;
            }
            final double expConstant = calculateExpConstant();
            final double negative_exponent = -expConstant * x;
			final double positive_exponent = expConstant * (x - inverseTime);
			if (positive_exponent > MAX_EXPONENT || negative_exponent < -MAX_EXPONENT) {
				return 0.0;
			}
            final double neg = Math.exp(negative_exponent);
            final double pos = Math.exp(positive_exponent);
            return expConstant * (0.5 - 0.5 * asymptote) * (4 * neg * pos) / ((pos + neg) * (pos + neg));
        }

        public double evaluateInverse(double y) {
            if (y < asymptote || y > 1.0) {
                return Double.NaN;
            }
            final double expConstant = calculateExpConstant();
            final double lhs = (y - (0.5 + 0.5 * asymptote)) / (0.5 - 0.5 * asymptote);
            return 0.5 * inverseTime - 0.5 * Math.log((1.0 - lhs) / (1.0 + lhs)) / expConstant;            
        }

		public String toString() {
			return (
                "Sigmoid(T=" + String.format("%.1f", inverseTime) +
                " d=" + String.format("%.1f", adherence) + 
                " m=" + String.format("%.1f", asymptote) + 
                "): (" + String.format("%.3f", evaluateInverse(0.0)) + ", 0.0)"
            );
		}
    }

	public static Boolean enabled = true;
	public static Boolean autoScratch = false;
	public static Boolean avoid56 = true;
	public static Boolean avoidPills = false;
	public static Integer scratchReallocationThreshold = 3;
	public static Integer avoidLNFactor = 1;
	public static Integer zureFactor = 1;
	public static Sigmoid hran = new Sigmoid(1.0, 1.5, -0.1);
    public static Sigmoid jacks = new Sigmoid(0.5, 3.0, -0.02);
    public static Sigmoid murizara = new Sigmoid(0.5, 3.0, -0.02);

    public static class Region {
        public final Input input;
        public final Side side;

        public Region(int lane) {
            if (lane < 0 || lane >= 16) {
                this.input = Input.BGA;
                this.side = Side.NONE;
            }
            else {
                this.input = (lane == 7 || lane == 15) ? Input.TT : Input.KEY;
                this.side = (lane < 8) ? Side.P1 : Side.P2;
            }
        }

        public Region(Input input, Side side) {
            this.input = input;
            this.side = side;
        }

        public static Region swapSides(Region region) {
            return new Region(
                region.input,
                switch (region.side) {
                    case P1 -> Side.P2;
                    case P2 -> Side.P1;
                    default -> region.side;
                }
            );
        }

        public static Region swapInput(Region region) {
            return new Region(
                switch (region.input) {
                    case BGA -> Input.BGA;
                    case KEY -> Input.TT;
                    case TT -> Input.KEY;
                    default -> region.input;
                },
                region.side
            );
        }

        public Set<Integer> lanes() {
            switch (input) {
                case KEY:
                    Set<Integer> keys = new HashSet<>();
                    if (side == Side.P1 || side == Side.BOTH) {
                        keys.addAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6));
                    }
                    if (side == Side.P1 || side == Side.BOTH) {
                        keys.addAll(Arrays.asList(8, 9, 10, 11, 12, 13, 14));
                    }
                    return keys;
                case TT:
                    Set<Integer> tts = new HashSet<>();
                    if (side == Side.P1 || side == Side.BOTH) {
                        tts.add(7);
                    }
                    if (side == Side.P2 || side == Side.BOTH) {
                        tts.add(15);
                    }
                    return tts;
                default:
                    return Set.of();
            }
        }

        public int scratch() {
            return switch (side) {
                case P1 -> 7;
                case P2 -> 15;
                default -> -1;
            };
        }

        public boolean includes(Region other) {
            if (this.input != other.input) {
                return false;
            }
            if (this.side == Side.BOTH) {
                return true;
            }
            return this.side == other.side;
        }

        public String toString() {
            return "Region(" + input + ", " + side + ")";
        }

        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || other.getClass() != this.getClass()) {
                return false;
            }
            Region otherRegion = (Region) other;
            return this.input == otherRegion.input && this.side == otherRegion.side;
        }
        
        public int hashCode() {
            return Objects.hash(input, side);
        }
    }

    public static class RegionSet extends HashSet<Region> {
        public RegionSet() {
            super();
        }

        public RegionSet(Region... regions) {
            super(Arrays.asList(regions));
        }

        public static RegionSet side(Side side) {
            return new RegionSet(
                new Region(Input.TT, side),
                new Region(Input.KEY, side)
            );
        }

        public static RegionSet input(Input input) {
            return new RegionSet(
                new Region(input, Side.P1),
                new Region(input, Side.P2)
            );
        }

        public static RegionSet all() {
            return new RegionSet(
                new Region(Input.TT, Side.P1),
                new Region(Input.KEY, Side.P1),
                new Region(Input.TT, Side.P2),
                new Region(Input.KEY, Side.P2)
            );
        }

        public RegionSet fullSides() {
            RegionSet result = new RegionSet(this.stream().map(Region::swapInput).toArray(Region[]::new));
            result.addAll(this);
            return result;
        }

        public RegionSet withMirror() {
            RegionSet result = new RegionSet(this.stream().map(Region::swapSides).toArray(Region[]::new));
            result.addAll(this);
            return result;
        }
    }

	public static final int normalize(int lane) {
        if (lane < 0 || lane >= 16) {
            return -1;
        }
		if (lane == 15) {
			return 7;
		}
		return (lane > 7) ? 14 - lane : lane;
	}

	public static class LaneData {
		public final int lane;
		public final Region region;
		public Note note;
		public LongNote head;
		public int source;
		public boolean hran;

		public LaneData(int lane) {
			this.lane = lane;
			this.region = new Region(lane);
			this.note = null;
			this.head = null;
			this.source = -1;
			this.hran = false;
		}

		public LaneData(int lane, Note note) {
			this.lane = lane;
			this.region = new Region(lane);
			this.note = note;
			if (note instanceof LongNote && !((LongNote) note).isEnd()) {
				this.head = (LongNote) note;
			}
			else {
				this.head = null;
			}
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

		public final NoteHeld held() {
			return (head != null) ? NoteHeld.LN : NoteHeld.SN;
		}
	}

	public static class FiveFingerFavorability {
		public Map<Integer, Double> fff;
		public Set<Integer> lanesOccupied;

		public final void initialize() {
			fff.clear();
			fff.put(127,1e1);
			fff.put(124,1e3); // 0,0,1,1,1,1,1
			fff.put(122,1e2); // 0,1,0,1,1,1,1
			fff.put(118,1e1); // 0,1,1,0,1,1,1
			fff.put(110,1e1); // 0,1,1,1,0,1,1
			fff.put( 94,1e1); // 0,1,1,1,1,0,1
			fff.put( 62,1e1); // 0,1,1,1,1,1,0
			fff.put(121,1e2); // 1,0,0,1,1,1,1
			fff.put(117,1e3); // 1,0,1,0,1,1,1
			fff.put(109,1e4); // 1,0,1,1,0,1,1
			fff.put( 93,1e5); // 1,0,1,1,1,0,1
			fff.put( 61,1e4); // 1,0,1,1,1,1,0
			fff.put(115,1e2); // 1,1,0,0,1,1,1
			fff.put(107,1e9); // 1,1,0,1,0,1,1
			fff.put( 91,1e4); // 1,1,0,1,1,0,1
			fff.put( 59,1e6); // 1,1,0,1,1,1,0
			fff.put(103,1e1); // 1,1,1,0,0,1,1
			fff.put( 87,1e1); // 1,1,1,0,1,0,1
			fff.put( 55,1e1); // 1,1,1,0,1,1,0
			fff.put( 79,1e1); // 1,1,1,1,0,0,1
			fff.put( 47,1e1); // 1,1,1,1,0,1,0
			fff.put( 31,1e3); // 1,1,1,1,1,0,0
			fff.put( 85,1e9); // 1,0,1,0,1,0,1
			fff.put( 27,1e8); // 1,1,0,1,1,0,0
			fff.put(108,1e8); // 0,0,1,1,0,1,1
			fff.put(120,1e5); // 0,0,0,1,1,1,1
			fff.put( 60,1e5); // 0,0,1,1,1,1,0
			fff.put( 82,2e8); // 0,1,0,0,1,0,1
			fff.put( 37,2e8); // 1,0,1,0,0,1,0
			fff.put( 74,5e8); // 0,1,0,1,0,0,1
			fff.put( 41,5e8); // 1,0,0,1,0,1,0
			if (avoid56) {
				// Remove combos with lanes "5 and 6" (1 and 2 in beatoraja numbering).
				fff = fff.entrySet().stream().filter(entry -> 
					(entry.getKey() & 6) != 6
				).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			}
			if (avoidPills) {
				// Remove combos with adjacent lanes (1-2 2-3 3-4 4-5 5-6 in beatoraja numbering).
				fff = fff.entrySet().stream().filter(entry -> {
					int key = entry.getKey();
					for (int i = 1; i < 5; i++) {
						int pill = 3 << i;
						if ((key & pill) == pill) {
							return false;
						}
					}
					return true;
				}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			}

			lanesOccupied.clear();

		}

		public FiveFingerFavorability() {
			fff = new HashMap<>();
			lanesOccupied = new HashSet<>();
			initialize();
		}

		private final void removeLane(int lane) {
			if (new Region(lane).input != Input.KEY) {
				return;
			}
			if (lanesOccupied.contains(normalize(lane))) {
				return;
			}

			// Remove this lane from all keys in the five-finger favorability map.
			// Any keys reduced to 0 (no lanes) are removed from the map entirely.
			Map<Integer, Double> fffAfterRemoval = fff.entrySet().stream().map(entry -> {
				final int bit = 1 << normalize(lane);
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
			lanesOccupied.add(lane);
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
			if (new Region(lane).input != Input.KEY) {
				return;
			}

			Map<Integer, Double> fffAfterPDF = fff.entrySet().stream().map(entry -> {
				final int bit = 1 << normalize(lane);
				final int key = entry.getKey();
				if ((key & bit) == 0) {
					return entry;
				}
				final double value = entry.getValue() * pdf;
				if (value <= 0.0) {
					return null;
				}
				return Map.entry(key, value);
			}).filter(entry -> entry != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
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

		public final Set<Integer> select(int minCount, java.util.Random rand) {
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

    
    public static class Allocator {
        private Map<Integer, Region> allocation;
        private Map<Pair<Input, NoteHeld>, RegionSet> allocationByNoteType;

        public Allocator() {
            this.allocation = new HashMap<>();
            this.allocationByNoteType = new HashMap<>();
        }

        public void clear() {
            this.allocation.clear();
            this.allocationByNoteType.clear();
        }

        public void log() {
            // Logger.getGlobal().info("Allocation: " + allocation + ", by note type: " + allocationByNoteType);
        }

        public void reserve(Region regionTarget, NoteHeld noteHeld) {
            Pair<Input, NoteHeld> noteType = new Pair<>(regionTarget.input, noteHeld);
            RegionSet regions = allocationByNoteType.getOrDefault(noteType, new RegionSet());
            regions.add(regionTarget);
            allocationByNoteType.put(noteType, regions);
			// Logger.getGlobal().info("Reserve: " + regionTarget + " " + noteHeld + " -> " + regions);
        }

        public void allocate(int laneSource, Region regionTarget, NoteHeld noteHeld) {
            allocation.put(laneSource, regionTarget);
            Pair<Input, NoteHeld> noteType = new Pair<>(regionTarget.input, noteHeld);
            RegionSet regions = allocationByNoteType.getOrDefault(noteType, new RegionSet());
            regions.add(regionTarget);
            allocationByNoteType.put(noteType, regions);
			// Logger.getGlobal().info("Allocate: " + laneSource + " " + regionTarget + " " + noteHeld + " -> " + regions);
        }

        public Region get(int lane) {
            return allocation.get(lane);
        }

        public RegionSet get(Input input, NoteHeld noteHeld) {
			RegionSet result = allocationByNoteType.getOrDefault(
				new Pair<>(input, noteHeld),
				new RegionSet()
			);
			// Logger.getGlobal().info("Get: " + input + " " + noteHeld + " -> " + result);
            return result;
        }

		public RegionSet available(Input input, NoteHeld noteHeld) {
			RegionSet available = RegionSet.input(input);
			// Logger.getGlobal().info("Available: " + input + " " + noteHeld + " -> " + available);
            if (input == Input.TT && noteHeld == NoteHeld.LN) {
                if (!get(Input.TT, NoteHeld.LN).isEmpty()) {
                    // Don't double up on BSS.
                    return new RegionSet();
                }
                available.removeAll(get(Input.KEY, NoteHeld.LN).fullSides());
                available.removeAll(get(Input.KEY, NoteHeld.SN).fullSides());
			}
            else if (input == Input.KEY && noteHeld == NoteHeld.LN) {
                available.removeAll(get(Input.TT, NoteHeld.LN).fullSides());
                available.removeAll(get(Input.TT, NoteHeld.SN).fullSides());
			}
            else if (input == Input.TT && noteHeld == NoteHeld.SN) {
                available.removeAll(get(Input.TT, NoteHeld.LN));
                available.removeAll(get(Input.KEY, NoteHeld.LN).fullSides());
                available.removeAll(get(Input.KEY, NoteHeld.SN).fullSides());
			}
            else if (input == Input.KEY && noteHeld == NoteHeld.SN) {
                available.removeAll(get(Input.TT, NoteHeld.LN).fullSides());
                available.removeAll(get(Input.TT, NoteHeld.SN).fullSides());
			}

			// Logger.getGlobal().info("Available after removals: " + input + " " + noteHeld + " -> " + available);
            return available;
		}

        public int count(Region region) {
            return (int) allocation.values().stream().filter(r -> r.input == region.input && r.side == region.side).count();
        }
    }


	public static class State {
		private java.util.Random rand;
		private BMSModel model;
		private static final int LANES = 16;
		private LaneData[] data;
		private Map<Side, FiveFingerFavorability> fff;
        private Allocator allocator;
		private Map<Integer, Integer> permuter;
		private Map<Strategy, Integer> strategy;

		private final double levenshteinDistance(Note n1, Note n2) {
			if (n1 == null || n2 == null) {
				return 1.0;
			}
			int w1 = n1.getWav();
			int w2 = n2.getWav();
			if (w1 < 0 || w2 < 0) {
				return 1.0;
			}
			if (w1 > model.getWavList().length || w2 > model.getWavList().length) {
				return 1.0;
			}
			String fn1 = model.getWavList()[w1];
			String fn2 = model.getWavList()[w2];
			if (fn1 == null || fn2 == null) {
				return 1.0;
			}
			if (fn1.isEmpty() || fn2.isEmpty()) {
				return 1.0;
			}
			return ((double) Fourteenizer.levenshteinDistance(fn1, fn2)) / Math.max(fn1.length(), fn2.length());
		}

		// So you're saying there's a chance.
		private final double MIN_PDF = 1e-24;

		
		public State(long seed, BMSModel model) {
			this.rand = new java.util.Random(seed);
			this.model = model;
			this.data = new LaneData[LANES];
			for (int i = 0; i < LANES; i++) {
				this.data[i] = new LaneData(i);
			}
			this.fff = new HashMap<>();
			this.fff.put(Side.P1, new FiveFingerFavorability());
			this.fff.put(Side.P2, new FiveFingerFavorability());
            this.allocator = new Allocator();
			this.permuter = new HashMap<>();
			this.strategy = new HashMap<>();
		}

		public int count(Strategy strategy) {
			return this.strategy.getOrDefault(strategy, 0);
		}

		public String reportStrategies() {
			return strategy.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
				.map(entry -> entry.getKey().name() + ": " + entry.getValue())
				.collect(Collectors.joining("\n\t"));
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
			if (new Region(lane).input != Input.KEY) {
				// Early exit - non-key lanes don't care about "jacks".
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
			return jacks.evaluate(data[lane].since(time));
		}

		private double pdfMurizaraFromPlacingKeyNote(int lane, long time) {
            Region region = new Region(lane);
			if (region.input != Input.KEY) {
				// Early exit - non-key lanes have a different calculation for murizara.
				return 1.0;
			}
			if (permuter.containsValue(lane)) {
				// Early exit - already receiving a note this update.
				return 0.0;
			}
            if (allocator.count(new Region(Input.TT, region.side)) > 0) {
                // Early exit - a scratch note has already been allocated to this side.
				return 0.0;
            }
			if (!anyHistory()) {
				// Early exit - no note history to influence positioning yet.
				return 1.0;
			}
			// Calculate the time since the last note in the scratch lane on the same side,
			// and use that to calculate the sigmoid influence on the PDF.
			return murizara.evaluate(data[region.scratch()].since(time));
		}

		private double pdfMurizaraFromPlacingScratch(int lane, long time) {
            Region region = new Region(lane);
			if (region.input != Input.KEY) {
				// Early exit - key lanes have a different calculation for murizara.
				return 1.0;
			}
			if (permuter.containsValue(region.scratch())) {
				// Early exit - already receiving a note this update.
				return 0.0;
			}
            if (allocator.count(new Region(Input.KEY, region.side)) > 0) {
                // Early exit - a key note has already been allocated to this side.
				return 0.0;
            }
			if (!anyHistory()) {
				// Early exit - no note history to influence positioning yet.
				return 1.0;
			}
			// Calculate the time since the last note in the scratch lane on the same side,
			// and use that to calculate the sigmoid influence on the PDF.
			return murizara.evaluate(data[lane].since(time));
		}

		private final void applyPDF(long time) {
			for (int i = 0; i < LANES; i++) {
                Region region = new Region(i);
				if (region.input != Input.KEY) {
					continue;
				}
				final double pdf = pdfJack(i, time) * pdfMurizaraFromPlacingKeyNote(i, time);
				final double pdfZure = pdfJack(i, time * Fourteenizer.zureFactor) * pdfMurizaraFromPlacingKeyNote(i, time * Fourteenizer.zureFactor);
				if (pdfZure <= 0.0) {
					fff.get(region.side).removeLane(normalize(i));
					continue;
				}
				fff.get(region.side).applyPDF(normalize(i), pdf);
			}
		}

		private final void removeLane(int lane) {
            Region region = new Region(lane);
			fff.get(region.side).removeLane(normalize(lane));
			// Logger.getGlobal().info("Removed lane: " + lane + " from " + region.side + " -> " + fff.get(region.side).fff);
		}

		private final void prepareState() {
			fff.put(Side.P1, new FiveFingerFavorability());
			fff.put(Side.P2, new FiveFingerFavorability());
		}

		public void protectLN() {
			for (int i = 0; i < LANES; i++) {
				if (data[i].head != null) {
					// Make sure the lane appears as a value in the permuter without assigning it a legal key.
					// Logger.getGlobal().info("Protecting LN: " + i);
					permuter.put(1000+i, i);
                    allocator.reserve(new Region(i), NoteHeld.LN);
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
							// Logger.getGlobal().info("Resolving LN: " + i + " -> " + laneHead);
							permuter.put(i, laneHead);
							strategy.merge(Strategy.CONTINUITY, 1, Integer::sum);
                            allocator.allocate(i, new Region(laneHead), NoteHeld.SN);
							removeLane(laneHead);
							break;
						}
					}
				}
			}
		}


        private Side compare(Map<Side, Integer> map, boolean resolveTie) {
            if (map.get(Side.P1) < map.get(Side.P2)) {
                return Side.P1;
            }
            if (map.get(Side.P2) < map.get(Side.P1)) {
                return Side.P2;
            }
            if (resolveTie) {
                return new ArrayList<>(Arrays.asList(Side.P1, Side.P2)).get(rand.nextInt(2));
            }
            return Side.BOTH;
        }

        private boolean tryAvailableRegions(int laneSource, Input input, NoteHeld noteHeld) {
			RegionSet availableRegions = allocator.available(input, noteHeld);
            // Logger.getGlobal().info("Available regions for " + laneSource + " " + input + " " + noteHeld + ": " + availableRegions);
            if (availableRegions.isEmpty()) {
                throw new IndexOutOfBoundsException("No available regions found for input: " + input + " and noteHeld: " + noteHeld);
            }
			if (availableRegions.size() == 1) {
				allocator.allocate(
                    laneSource,
                    availableRegions.iterator().next(),
                    noteHeld
                );
				return true;
			}
            return false;
        }

		private boolean placeBSS(int laneSource, long time) {
            if (tryAvailableRegions(laneSource, Input.TT, NoteHeld.SN)) {
                return true;
            }

			// Otherwise, map to the TT lane on the side
			// that triggers fewer murizara preventions.
			Map<Side, Integer> protections = new HashMap<>();
			protections.put(Side.P1, 0);
			protections.put(Side.P2, 0);
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				if (region.input != Input.KEY) {
					continue;
				}
				protections.merge(region.side, (
					(rand.nextDouble() > pdfMurizaraFromPlacingScratch(i, time)) ? 1 : 0
				), Integer::sum);
			}

            Side side = compare(protections, true);
            allocator.allocate(laneSource, new Region(Input.TT, side), NoteHeld.LN);
            return true;
		}

		private boolean placeKeyLN(int laneSource, long time) {
            if (tryAvailableRegions(laneSource, Input.KEY, NoteHeld.LN)) {
                return true;
            }

			// If either side currently contains an active key LN,
			// choose the side with more active LN.
			Map<Side, Integer> activeLNs = new HashMap<>();
			activeLNs.put(Side.P1, 0);
			activeLNs.put(Side.P2, 0);
			Map<Side, Integer> protections = new HashMap<>();
			protections.put(Side.P1, allocator.count(new Region(Input.KEY, Side.P1)));
			protections.put(Side.P2, allocator.count(new Region(Input.KEY, Side.P2)));
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				if (region.input != Input.KEY) {
					continue;
				}
                Side otherSide = (region.side == Side.P1) ? Side.P2 : Side.P1;
				if (data[i].head != null) {
                    // TRICKY: file LNs on the opposite side so the less-than 
                    // comparator chooses the side with more active LNs.
					activeLNs.merge(otherSide, 1, Integer::sum);
				}
				protections.merge(region.side, (
					(rand.nextDouble() > pdfJack(i, time)) ? 1 : 0
				) + (
					(rand.nextDouble() > pdfMurizaraFromPlacingKeyNote(i, time)) ? 1 : 0
				), Integer::sum);
			}
            // If there is a side with more active LNs, allocate to that side.
            Side side = compare(activeLNs, false);
            if (side != Side.BOTH) {
                allocator.allocate(laneSource, new Region(Input.KEY, side), NoteHeld.LN);
                return true;
            }
			// Otherwise, choose a side that triggers fewer murizara & jack preventions.
            side = compare(protections, true);
            allocator.allocate(laneSource, new Region(Input.KEY, side), NoteHeld.LN);
            return true;
		}

		private boolean placeTT(int laneSource, long time) {
            if (tryAvailableRegions(laneSource, Input.TT, NoteHeld.SN)) {
                return true;
            }

			Map<Side, Integer> protections = new HashMap<>();
			protections.put(Side.P1, 0);
			protections.put(Side.P2, 0);
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				if (region.input == Input.TT) {
					continue;
				}
				protections.merge(region.side, (
					(rand.nextDouble() > pdfMurizaraFromPlacingScratch(i, time)) ? 1 : 0
				), Integer::sum);
			}
			// Otherwise, map to the TT lane on the side
			// that triggers fewer murizara preventions.
            Side side = compare(protections, true);
            allocator.allocate(laneSource, new Region(Input.TT, side), NoteHeld.SN);
            return true;
		}

		private boolean placeKeySN(int laneSource, long time) {
            if (tryAvailableRegions(laneSource, Input.KEY, NoteHeld.SN)) {
                return true;
            }

			// If there is a currently active key LN,
			// choose an available side that triggers fewer LN avoidances.
			Map<Side, Integer> avoidLNs = new HashMap<>();
			avoidLNs.put(Side.P1, 0);
			avoidLNs.put(Side.P2, 0);
			Map<Side, Integer> protections = new HashMap<>();
			protections.put(Side.P1, allocator.count(new Region(Input.KEY, Side.P1)));
			protections.put(Side.P2, allocator.count(new Region(Input.KEY, Side.P2)));
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				if (region.input != Input.KEY) {
					continue;
				}
				if (data[i].head != null) {
					avoidLNs.merge(region.side, 1, Integer::sum);
				}
				protections.merge(region.side, (
					(rand.nextDouble() > pdfJack(i, time)) ? 1 : 0
				) + (
					(rand.nextDouble() > pdfMurizaraFromPlacingKeyNote(i, time)) ? 1 : 0
				), Integer::sum);
			}
			if (avoidLNs.get(Side.P1) + avoidLNs.get(Side.P2) > 0) {
				boolean testP1 = rand.nextDouble() < Math.exp(-avoidLNs.get(Side.P1)*avoidLNFactor);
				boolean testP2 = rand.nextDouble() < Math.exp(-avoidLNs.get(Side.P2)*avoidLNFactor);
				if (!testP1 && testP2) {
					allocator.allocate(laneSource, new Region(Input.KEY, Side.P2), NoteHeld.SN);
					return true;
				}
				if (!testP2 && testP1) {
					allocator.allocate(laneSource, new Region(Input.KEY, Side.P1), NoteHeld.SN);
					return true;
				}
            }
            // Side side = compare(protections, false);
            allocator.allocate(laneSource, new Region(Input.KEY, Side.BOTH), NoteHeld.SN);
            return true;
		}

		private void allocateOnlyNoteType(
			TimeLine tl,
			Input input,
			NoteHeld noteHeld,
			boolean mapScratchToKey
		) {
            Input convertedInput = (mapScratchToKey) ? Input.KEY : input;

			for (int i = 0; i < LANES; i++) {
				if (permuter.containsKey(i)) {
					// Already have a mapping for this source note.
					continue;
				}

				Note note = tl.getNote(i);
				if (note == null) {
					// No notes here.
					continue;
				}

				if (note instanceof LongNote && ((LongNote) note).isEnd()) {
					// LN ends are already taken care of by resolveLN().
					continue;
				}

				LaneData incomingNote = new LaneData(i, note);
				if (incomingNote.region.input != input) {
                    continue;
                }
                if (incomingNote.held() != noteHeld) {
					continue;
				}

				if (convertedInput == Input.TT) {
                    if (noteHeld == NoteHeld.LN) {
                        placeBSS(i, tl.getTime());
                    }
                    else {
                        placeTT(i, tl.getTime());
                    }
				}
				else if (convertedInput == Input.KEY) {
                    if (noteHeld == NoteHeld.LN) {
                        placeKeyLN(i, tl.getTime());
                    }
                    else {
                        placeKeySN(i, tl.getTime());
                    }
				}
			}
		}
		
		private void allocateIncomingNotes(TimeLine tl, boolean mapScratchToKey) {
			allocateOnlyNoteType(tl, Input.TT, NoteHeld.LN, mapScratchToKey);
			allocateOnlyNoteType(tl, Input.KEY, NoteHeld.LN, mapScratchToKey);
			allocateOnlyNoteType(tl, Input.TT, NoteHeld.SN, mapScratchToKey);
			allocateOnlyNoteType(tl, Input.KEY, NoteHeld.SN, mapScratchToKey);
		}

		private boolean reallocateScratch(TimeLine tl) {
            int countP1 = allocator.count(new Region(Input.KEY, Side.P1));
            int countP2 = allocator.count(new Region(Input.KEY, Side.P2));
            int countBOTH = allocator.count(new Region(Input.KEY, Side.BOTH));
			boolean ignoreP1 = allocator.count(new Region(Input.TT, Side.P1)) > 0;
			boolean ignoreP2 = allocator.count(new Region(Input.TT, Side.P2)) > 0;
            int fffP1 = ignoreP1 ? 0 : fff.get(Side.P1).maxLaneCount();
            int fffP2 = ignoreP2 ? 0 : fff.get(Side.P2).maxLaneCount();
            if (countP1 > scratchReallocationThreshold) {
                return true;
            }
            if (countP1 > fffP1) {
                return true;
            }
            if (countP2 > scratchReallocationThreshold) {
                return true;
            }
            if (countP2 > fffP2) {
                return true;
            }   
            if (countP1 + countP2 + countBOTH > 2 * scratchReallocationThreshold) {
                return true;
            }
            if (countP1 + countP2 + countBOTH > fffP1 + fffP2) {
                return true;
            }			
			// if (fffP1 == 0 || fffP2 == 0) {
			// 	Logger.getGlobal().info("Reallocation threshold not met @ " + tl.getTime() + ": P1 " + countP1 + " < " + fffP1 + ", P2" + countP2 + " < " + fffP2 + ", BOTH " + (countP1 + countP2 + countBOTH) + " < " + (fffP1 + fffP2));
			// }
            return false;
		}

		private boolean mapNoteRAN(TimeLine tl, int laneSource) {
			Note note = tl.getNote(laneSource);
			if (note == null) {
				// No notes here. (Don't care about hidden notes.)
				return false;
			}
			if (permuter.containsKey(laneSource)) {
				// Already have a mapping for this source note.
				return false;
			}
			if (allocator.get(laneSource) == null) {
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
			Region allocatedRegion = allocator.get(laneSource);
			Set<Integer> filteredHistory = new HashSet<>();
			double pdf[] = new double[LANES];
			double cdf = 0.0;
			for (int i = 0; i < LANES; i++) {
                Region region = new Region(i);
				if (permuter.containsValue(i)) {
					// Already have a mapping to this lane.
					pdf[i] = 0.0;
					continue;
				}
				if (data[i].note == null) {
					// No history for this lane.
					pdf[i] = 0.0;
					continue;
				}
				if (data[i].head != null) {
					// Active LN, can't place here.
					pdf[i] = 0.0;
					continue;
				}
				// if (data[i].hran) {
				// 	// Last note was mapped with H-RAN, can't follow with RAN.
				// 	continue;
				// }
				if (!allocatedRegion.includes(region)) {
					// Not a selectable region for this note.
					pdf[i] = 0.0;
					continue;
				}
				if (laneSource != data[i].source) {
					// Source lane doesn't match.
					pdf[i] = 0.0;
					continue;
				}
				// Passes all filters.
				if (region.input == Input.KEY) {
					if (fff.get(region.side).hasMatching(i)) {
						pdf[i] = fff.get(region.side).sumPDF(normalize(i));
					}
					else {
						// No matching five-finger favorability combo.
						pdf[i] = 0.0;
						continue;
					}
				}
				else {
					pdf[i] = 1.0;
				}
				cdf += pdf[i];
				pdf[i] *= (1.0 - hran.evaluate(data[i].since(time)));
				pdf[i] *= (1.0 - levenshteinDistance(data[i].note, note));
				filteredHistory.add(i);
			}
			// Run the RAN vs. H-RAN trigger for each note in that filtered history.
			// If on any note, the trigger doesn't fire:
			// - use the same mapping as that previous note
			// - remove that lane from the selectable lanes
			// - continue to the next incoming note
			// Logger.getGlobal().info("PDF: " + Arrays.toString(pdf));
			if (cdf <= 0.0) {
				return false;
			}
			double r = rand.nextDouble();
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				r -= pdf[i] / cdf;
				if (r <= 0.0) {
					permuter.put(laneSource, i);
					removeLane(i);
					strategy.merge(Strategy.RAN, 1, Integer::sum);
					// Logger.getGlobal().info("Mapped note RAN: " + laneSource + " -> " + i);
					return true;
				}
			}
			return false;
		}

		private boolean mapNoteHRAN(TimeLine tl, int laneSource) {
			if (tl.getNote(laneSource) == null && tl.getHiddenNote(laneSource) == null) {
				// No notes here.
				return false;
			}
			if (permuter.containsKey(laneSource)) {
				// Already have a mapping for this source note.
				return false;
			}
			if (allocator.get(laneSource) == null) {
				// Don't have an allocation for this source note.
				return false;
			}

			// Choose among the selectable lanes using the flattened
			// five-finger favorability PDF across selectable sides for that note.
			Region allocatedRegion = allocator.get(laneSource);
			double pdf[] = new double[LANES];
			boolean allSkipped = true;
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				if (region.input != Input.KEY) {
					continue;
				}
				if (permuter.containsValue(i)) {
					continue;
				}
				if (!allocatedRegion.includes(region)) {
					continue;
				}
				// Logger.getGlobal().info("Summing PDF for " + region.side + " " + i + " within " + fff.get(region.side).fff + " -> " + fff.get(region.side).sumPDF(normalize(i)));
				pdf[i] = fff.get(region.side).sumPDF(normalize(i));
				allSkipped = false;
			}
			if (allSkipped) {
				Logger.getGlobal().info("All lanes skipped @ " + tl.getTime() + ": " + fff.get(Side.P1).fff + ", " + fff.get(Side.P2).fff);
				return false;
			}
			// Logger.getGlobal().info("PDF: " + Arrays.toString(pdf));
			double sum = Arrays.stream(pdf).sum();
			if (sum <= 0.0) {
				Logger.getGlobal().info("PDF sum is 0.0 @ " + tl.getTime() + ": " + Arrays.toString(pdf));
				return false;
			}
			double r = rand.nextDouble();
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				r -= pdf[i] / sum;
				if (r <= 0.0) {
					permuter.put(laneSource, i);
					removeLane(i);
					strategy.merge(Strategy.HRAN, 1, Integer::sum);
					// Logger.getGlobal().info("Mapped note HRAN: " + laneSource + " -> " + i);
					return true;
				}
			}
			return false;
		}

		private boolean mapScratch(TimeLine tl, int laneSource) {
            if (new Region(laneSource).input != Input.TT) { 
                // Only scratch lanes can map to other scratch lanes.
                return false;
            }
			if (tl.getNote(laneSource) == null && tl.getHiddenNote(laneSource) == null) {
				// No notes here.
				return false;
			}
			if (permuter.containsKey(laneSource)) {
				// Already have a mapping for this source note.
				return false;
			}
			if (allocator.get(laneSource) == null) {
				// Don't have an allocation for this source note.
				return false;
			}
			Region allocatedRegion = allocator.get(laneSource);
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				if (region.input != Input.TT) {
					continue;
				}
				if (permuter.containsValue(i)) {
					continue;
				}
				if (!allocatedRegion.includes(region)) {
					continue;
				}
                permuter.put(laneSource, region.scratch());
				strategy.merge(Strategy.SCRATCH, 1, Integer::sum);
				// Logger.getGlobal().info("Mapped scratch: " + laneSource + " -> " + region.scratch());
				return true;
			}
			return false;
		}

		private boolean mapWorstCase(TimeLine tl, int laneSource) {
			Map<Integer, Double> worstCase = new HashMap<>();
			for (int i = 0; i < LANES; i++) {
				Region region = new Region(i);
				if (region.input != Input.KEY) {
					continue;
				}
				if (permuter.containsValue(i)) {
					continue;
				}
				worstCase.put(i, pdfJack(i, tl.getTime()) * pdfMurizaraFromPlacingKeyNote(i, tl.getTime()));
			}
			if (worstCase.isEmpty()) {
				return false;
			}
			Map.Entry<Integer, Double> entry = worstCase.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
			if (entry == null) {
				Logger.getGlobal().info("No worst case mapping @ " + tl.getTime() + ": " + worstCase);
				return false;
			}
			int laneTarget = entry.getKey();
			permuter.put(laneSource, laneTarget);
			removeLane(laneTarget);
			strategy.merge(Strategy.WORST_CASE, 1, Integer::sum);
			Logger.getGlobal().info("Worst case mapping at random: " + laneSource + " among " + worstCase + " -> " + laneTarget);
			return true;
		}

		private boolean mapKeys(TimeLine tl, boolean mapScratchToKey) {
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

			if (!mapScratchToKey) {
				for (int i : hasNote) {
					if (!mapScratch(tl, i)) {
						remaining.add(i);
					}
				}
				hasNote = remaining;
				remaining = new HashSet<>();
			}
			
			for (int i : hasNote) {
				if (!mapNoteRAN(tl, i)) {
					remaining.add(i);
				}
			}
            hasNote = remaining;
            remaining = new HashSet<>();
			

			for (int i : hasNote) {
				if (!mapNoteHRAN(tl, i)) {
					remaining.add(i);
				}
			}
            hasNote = remaining;
            remaining = new HashSet<>();

			for (int i : hasNote) {
				if (!mapWorstCase(tl, i)) {
					// Nothing could be done for this note :(
					return false;
				}
			}

			return true;
		}

		public void performPermutation(TimeLine tl) {
			// Logger.getGlobal().info("Performing permutation: " + permuter);

			Note[] notes = new Note[LANES];
			Note[] hnotes = new Note[LANES];
			for (int i = 0; i < LANES; i++) {
				notes[i] = tl.getNote(i);
				hnotes[i] = tl.getHiddenNote(i);
				tl.setNote(i, null);
				tl.setHiddenNote(i, null);
			}
			
			RegionSet regions = new RegionSet();
			for (int i = 0; i < LANES; i++) {
				int mapped = permuter.getOrDefault(i, i);
				if (notes[i] != null || hnotes[i] != null) {
					if (!permuter.containsKey(i)) {
						Logger.getGlobal().warning("No mapping for note: " + i + " -> " + i + " on timeline @ " + tl.getTime());
						strategy.merge(Strategy.FAILURE, 1, Integer::sum);
					}
					// Logger.getGlobal().info("Setting note: " + i + " -> " + mapped);
					tl.setNote(mapped, notes[i]);
					tl.setHiddenNote(mapped, hnotes[i]);
					data[mapped].source = i;
					regions.add(new Region(mapped));
				}
			}
			if (regions.containsAll(RegionSet.side(Side.P1)) || regions.containsAll(RegionSet.side(Side.P2))) {
				Logger.getGlobal().warning("Full side allocation @ " + tl.getTime() + ": " + permuter);
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
				if (notes[i] instanceof LongNote) {
                    LongNote ln = (LongNote) notes[i];
                    if (ln.isEnd()) {
                        data[i].head = null;
                    } else {
                        data[i].head = ln;
                    }
                }
			}
		}

		public boolean process(TimeLine tl) {
			// Logger.getGlobal().info("Processing TL: " + tl.getTime());

			// Prepare state machine for this round of updates.
			prepareState();

			// Handle active LN.
			boolean mapScratchToKey = true;
			try {
				allocator.clear();
				resolveLN(tl);
				protectLN();
				allocateIncomingNotes(tl, false);
				mapScratchToKey = reallocateScratch(tl) || Fourteenizer.autoScratch;
			} catch (Exception e) {
				mapScratchToKey = true;
			}
			if (mapScratchToKey) {
				try {
					allocator.clear();
					resolveLN(tl);
					protectLN();
					allocateIncomingNotes(tl, true);
				} catch (Exception e) {
					Logger.getGlobal().warning("Error allocating notes: " + e.getMessage());
				}
			}
			allocator.log();

			// Apply PDF to the key lanes to set up the five-finger favorability.
			applyPDF(tl.getTime());
			// Logger.getGlobal().info("PDF: " + fff.get(Side.P1).fff + " " + fff.get(Side.P2).fff);

			// Map the keys.
			mapKeys(tl, mapScratchToKey);
			// Logger.getGlobal().info("Permuter: " + permuter);

			// Actually perform the permutation.
			performPermutation(tl);
			updateState(tl);

			return mapScratchToKey;
		}
	}
}
