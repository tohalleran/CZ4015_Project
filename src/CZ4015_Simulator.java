import java.util.*;
import java.io.*;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;

class CZ4015_Simulator {
	static List<List<Double>> sheetData;
	static int totalCalls, nBlockedCalls, nDroppedCalls;
	static int maxFELSize = 0;

	static PriorityQueue<Event> fel;
	static int[] stations;
	static double now;

	public static void main(String[] args) {
		importData();
		totalCalls = sheetData.size();
		// Run the simulation
		for (int i = 0; i < 10; i++) {
			nBlockedCalls = 0;
			nDroppedCalls = 0;
			
			System.out.println("Trial " + (i+1) + ": ");
			
			runSimulation();
			
			// Calculate the statistics
			double blockPercentage = ((double) nBlockedCalls / totalCalls) * 100;
			double dropPercentage = ((double) nDroppedCalls / totalCalls) * 100;
//			System.out.println(String.format("%.2f", blockPercentage) + " " + String.format("%.2f", dropPercentage));
			System.out.println("Number of blocked calls: " + nBlockedCalls + "\t Percentage of blocked calls: "
					+ String.format("%.2f", blockPercentage));
			System.out.println("Number of dropped calls: " + nDroppedCalls + "\t Percentage of dropped calls: "
					+ String.format("%.2f", dropPercentage));
			System.out.println("Maximum FEL Size: " + maxFELSize);
			System.out.println();
		}
	}

	static void runSimulation() {
		stations = new int[20];
		now = 0.0;
		fel = new PriorityQueue<Event>(200, new Comparator<Event>() {
			public int compare(Event o1, Event o2) {
				if (o1.time < o2.time)
					return -1;
				if (o1.time > o2.time)
					return 1;
				return 0;
			}
		});

		// Add first event
		List<Double> firstCall = sheetData.get(0);
		fel.add(new Event(EventType.INITIALIZE, firstCall.get(0), firstCall.get(1), firstCall.get(2) - 1,
				firstCall.get(3), firstCall.get(4), Math.random() > 0.5 ? -1 : 1));

		// Double check if current next incoming call to be initialize
		int currentCallLine = 1;

		while (!fel.isEmpty()) {
			maxFELSize = Math.max(fel.size(), maxFELSize);
			Event event = fel.poll();
			now = event.time;
//			if(Math.round(now) % 140 == 0){
//				System.out.print(/*String.format("%.2f", now) + " " + */nBlockedCalls + " " + nDroppedCalls);
//				System.out.println();
//			}
			// Call Event Handler
			if (event.eventType == EventType.INITIALIZE) {
				if (currentCallLine == event.callLine) {
					handleInitialize(event, currentCallLine);
					currentCallLine += 1;
				} else
					System.err.println("Wrong call line");
			} else if (event.eventType == EventType.HANDOVER)
				handleHandover(event);
			else
				handleTermination(event);
		}
	}

	static void handleInitialize(Event event, int nextCallLine) {
		// System.out.println("Handled Init");
		// Add next call to FEL
		if (nextCallLine < 10000) {
			List<Double> nextCall = sheetData.get(nextCallLine);
			// System.out.println(nextCallLine);
			fel.add(new Event(EventType.INITIALIZE, nextCall.get(0), nextCall.get(1), nextCall.get(2) - 1,
					nextCall.get(3), nextCall.get(4), Math.random() > 0.5 ? -1 : 1));
		}

		//totalCalls += 1;

		pathSelectionHelper(event);

	}

	static void handleHandover(Event event) {
		// System.out.println("Handled Hand");
		// Open channel used from the previous base station
		int previousStation = event.direction == 1 ? event.currentStation - 1 : event.currentStation + 1;
		stations[previousStation] -= 1;
		pathSelectionHelper(event);
	}

	/**
	 * Used by both the handleInitialize and handleHandover Methods This helper
	 * method determines the next correct event (either HANDOVER or TERMINATION)
	 * for the one specific call
	 * 
	 * @param event
	 *            This is the specific call
	 */
	static void pathSelectionHelper(Event event) {
		// Check if call will be blocked
		if (event.eventType == EventType.INITIALIZE && stations[event.currentStation] >= 9) {
			// if (stations[event.currentStation] >= 9)
			nBlockedCalls += 1;

		} else if (event.eventType == EventType.HANDOVER && stations[event.currentStation] >= 10) {
			// if (stations[event.currentStation] >= 10)
			nDroppedCalls += 1;

			// New call event will not be created and added to the fel
			// The call event ends here
		} else {
			// Call was not blocked, add call to a channel at current station
			stations[event.currentStation] += 1; // Mark used channel for call
			// Double callDurationDist = event.velocity * event.callDuration;
			// Units is seconds
			double maxCallTimeAtCurrentStation = event.eventType == EventType.HANDOVER ? (2.0 / event.velocity) * 3600
					: ((Math.random() * 2.0) / event.velocity) * 3600;

			// Check if call will terminate at current station
			if (maxCallTimeAtCurrentStation > event.callDuration) {
				event.time = now + event.callDuration;
				event.callDuration = 0.0;
				event.eventType = EventType.TERMINATION;
				// fel.add(event);
			} else {
				// Call will be handed over to next station
				event.callDuration -= maxCallTimeAtCurrentStation; // Update
																	// call
																	// duration
				int nextStation = event.currentStation + event.direction; // -1
																			// for
																			// left
																			// and
																			// +1
																			// for
																			// right

				event.time = now + maxCallTimeAtCurrentStation; // Update time
																// of next event
																// for call
				// Check if call moves off highway
				if (nextStation < 0 || nextStation > 19) {
					// Event time was updated up top for both paths
					event.eventType = EventType.TERMINATION;
					event.callDuration = 0.0; // Unnecessary but used as checker
					// fel.add(event);
				} else {
					// Call will remain on the highway
					// Event time was updated up top for both paths
					// Call duration was updated up top
					event.currentStation = nextStation; // Update current
														// station to next
														// station
					event.eventType = EventType.HANDOVER; // Update event type
					// fel.add(event); // Add event to fel

				}
			}
			// Add update event for call!!
			fel.add(event);
		}
	}

	static void handleTermination(Event event) {
		stations[event.currentStation] -= 1;
		// New call event will not be created and added to the fel
		// The call event ends here
	}

	static void importData() {
		String filepath = "/Users/tonyohalleran/Developer/CZ4015 Project/PCS_TEST_DETERMINSTIC_1718S2.xls";
		sheetData = new ArrayList<List<Double>>();
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(filepath);
			HSSFWorkbook workbook = new HSSFWorkbook(fis);
			HSSFSheet sheet = workbook.getSheetAt(0);
			Iterator<Row> rows = sheet.rowIterator();
			rows.next();
			while (rows.hasNext()) {
				HSSFRow row = (HSSFRow) rows.next();
				Iterator<Cell> cells = row.cellIterator();

				List<Double> data = new ArrayList<Double>();
				while (cells.hasNext()) {
					HSSFCell cell = (HSSFCell) cells.next();
					// if (cell.getCellType() == HSSFCell.CELL_TYPE_NUMERIC)
					data.add(Math.round(cell.getNumericCellValue() * 1000.0) / 1000.0);
				}

				sheetData.add(data);
			}

			workbook.close();
			fis.close();

		} catch (FileNotFoundException ex) {
			System.out.println("Wrong file path");
			ex.printStackTrace();
		} catch (IOException ioe) {
			System.out.println("IO Exception");
			ioe.printStackTrace();
		}

		// List<Double> rowData = sheetData.get(0);
		// for(int i = 0; i < rowData.size(); i++){
		// System.out.println(rowData.get(i));
		// }
	}
}

enum EventType {
	INITIALIZE, HANDOVER, TERMINATION
}

// Each event instance represents a single specific call throughout its life
// cycle
class Event {
	EventType eventType;
	double time, callDuration, velocity;
	int currentStation;
	double callLine;
	int direction;

	Event(EventType eventType, double callLine, double time, Double currentStation, double duration, double velocity,
			int direction) {
		this.eventType = eventType;
		this.callLine = callLine;
		this.time = time;
		this.currentStation = currentStation.intValue();
		this.callDuration = duration;
		this.velocity = velocity;
		this.direction = direction;
	}
}
