import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.algebra.StatementPattern;
import org.openrdf.query.algebra.helpers.StatementPatternCollector;
import org.openrdf.query.parser.ParsedQuery;
import org.openrdf.query.parser.sparql.SPARQLParser;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;

import Common.FPInfo;
import Common.ItemInfo;
import Common.NeighborInfo;
import Common.HeapNode;
import Common.VariableInfo;
import Common.ResultInfo;

public class QueryProcessing {

	/**
	 * @param args
	 *            閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷烽敓楗鸿揪鎷烽敓鏂ゆ嫹涓�閿熸枻鎷烽敓鏂ゆ嫹閿熸帴纰夋嫹SPARQL閿熸枻鎷疯閿熸枻鎷烽敓鏂ゆ嫹keyword_sparql_Q3閿熸枻鎷风ず閿熸枻鎷烽敓鏂ゆ嫹
	 *            鐒堕敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹Dijkstra閿熷娉曢敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹閿熺耽eyword閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷疯矾閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷�
	 *            閿熸枻鎷烽敓鐭鎷烽敓鏂ゆ嫹閿熺Ц鏂ゆ嫹閿燂拷
	 */
	public static void main(String[] args) {
		try {
			String str = "";
			int cur_id = 0;
			String[] TermArr, TermArr1;

			int TopKNum = Integer.valueOf(args[2]);

			Date loadingStartTime = new Date();

			EnvironmentConfig envConfig1 = new EnvironmentConfig();
			envConfig1.setAllowCreate(true);
			Environment myDbEnvironment1 = new Environment(new File(args[0] + "/DistanceIndex"), envConfig1);

			// Open the database. Create it if it does not already exist.
			DatabaseConfig dbConfig1 = new DatabaseConfig();
			dbConfig1.setAllowCreate(true);
			Database myDatabase1 = myDbEnvironment1.openDatabase(null, "DistanceIndexDB", dbConfig1);

			EnvironmentConfig envConfig2 = new EnvironmentConfig();
			envConfig2.setAllowCreate(true);
			Environment myDbEnvironment2 = new Environment(new File(args[0] + "/StructuralIndex"), envConfig2);

			// Open the database. Create it if it does not already exist.
			DatabaseConfig dbConfig2 = new DatabaseConfig();
			dbConfig2.setAllowCreate(true);
			Database myDatabase2 = myDbEnvironment2.openDatabase(null, "StructuralIndexDB", dbConfig2);

			System.out.println("loading Entity ID Map...");
			HashMap<Integer, String> IDEntityMap = null;
			HashMap<String, Integer> EntityIDMap = CreateIDEntityMap(args[0] + "/entity_id_map.txt", IDEntityMap);

			System.out.println("loading RDF graph...");
			InputStream in = new FileInputStream(new File(args[0] + "/graph_adjacent_list.txt"));
			Reader inr = new InputStreamReader(in);
			BufferedReader br = new BufferedReader(inr);

			int neighbor_id = 0, label_id = 0, NodeNum = 0;
			int updateNum = 2;

			str = br.readLine();
			NodeNum = Integer.valueOf(str);

			NeighborInfo[][] adjacentList = new NeighborInfo[NodeNum][];

			str = br.readLine();
			while (str != null) {
				// count++;
				// if (count % 10000 == 4) {
				// System.out.println("++++++++" + count);
				// }

				str = str.trim();
				TermArr = str.split("\t");
				cur_id = Integer.valueOf(TermArr[0]);
				TermArr1 = TermArr[1].split(" ");
				adjacentList[cur_id] = new NeighborInfo[TermArr1.length / 4];
				for (int i = 0; i < TermArr1.length; i = i + 4) {
					neighbor_id = Integer.valueOf(TermArr1[i]);
					label_id = Integer.valueOf(TermArr1[i + 1]);

					adjacentList[cur_id][i / 4] = new NeighborInfo(neighbor_id, label_id);
					adjacentList[cur_id][i / 4].Distance = Integer.valueOf(TermArr1[i + 2]);
					adjacentList[cur_id][i / 4].Direction = Integer.valueOf(TermArr1[i + 3]);
				}

				str = br.readLine();
			}

			br.close();

			System.out.println("loading distance-based index ...");
			InputStream in175 = new FileInputStream(new File(args[0] + "/pivot.txt"));
			Reader inr175 = new InputStreamReader(in175);
			BufferedReader br175 = new BufferedReader(inr175);

			str = br175.readLine();
			cur_id = 0;
			HashSet<Integer> RNSet = new HashSet<Integer>();

			long count = 0;
			while (str != null) {
				// if (count % 100 == 0)
				// System.out.println("~~~~~~~~~~" + count);
				// count++;

				TermArr = str.split("\t");
				cur_id = Integer.valueOf(TermArr[0]);

				// InputStream in_rn = new FileInputStream(new File(
				// "E:/PengPeng/Data/KWS/Yago/Index/Pivots/" + cur_id));
				// Reader inr_rn = new InputStreamReader(in_rn);
				// BufferedReader br_rn = new BufferedReader(inr_rn);
				//
				// int[] b = new int[NodeNum];
				// str = br_rn.readLine();
				// str = str.substring(1, str.length() - 1);
				// TermArr = str.split(",");
				// for (int i = 0; i < TermArr.length; i++) {
				// TermArr[i] = TermArr[i].trim();
				// b[i] = Integer.valueOf(TermArr[i]);
				// }
				// br_rn.close();
				RNSet.add(cur_id);

				str = br175.readLine();
			}
			br175.close();

			System.out.println("loading structural index ...");
			InputStream in650 = new FileInputStream(new File(args[0] + "/p_weight.txt"));
			Reader inr650 = new InputStreamReader(in650);
			BufferedReader br650 = new BufferedReader(inr650);

			TreeMap<String, Integer> PredicateIDMap = new TreeMap<String, Integer>();
			TreeMap<Integer, String> IDPredicateMap = new TreeMap<Integer, String>();
			TreeMap<Integer, ItemInfo> IDItemMap = new TreeMap<Integer, ItemInfo>();
			TreeMap<Integer, Integer> LabelItemPosMap = new TreeMap<Integer, Integer>();
			int item_id = 0, p_id = 0;

			str = br650.readLine();
			count = 0;
			while (str != null) {
				TermArr = str.split(" ");

				PredicateIDMap.put(TermArr[0], Integer.valueOf(TermArr[1]));
				IDPredicateMap.put(Integer.valueOf(TermArr[1]), TermArr[0]);

				p_id = Integer.valueOf(TermArr[1]);
				LabelItemPosMap.put(p_id, item_id);

				IDItemMap.put(item_id, new ItemInfo(p_id, 0, 1));
				IDItemMap.put((item_id + 1), new ItemInfo(p_id, 0, 2));
				IDItemMap.put((item_id + 2), new ItemInfo(p_id, 0, 3));

				IDItemMap.put((item_id + 3), new ItemInfo(p_id, 1, 1));
				IDItemMap.put((item_id + 4), new ItemInfo(p_id, 1, 2));
				IDItemMap.put((item_id + 5), new ItemInfo(p_id, 1, 3));

				item_id = item_id + 6;

				str = br650.readLine();
			}

			br650.close();

			InputStream in70 = new FileInputStream(new File(args[0] + "/fp.out"));
			Reader inr70 = new InputStreamReader(in70);
			BufferedReader br70 = new BufferedReader(inr70);
			TreeSet<Integer> frequentPropertySet = new TreeSet<Integer>();
			TreeSet<Integer> frequentItemSet = new TreeSet<Integer>();
			TreeMap<Integer, ArrayList<Integer>> ItemFPListMap = new TreeMap<Integer, ArrayList<Integer>>();
			ArrayList<FPInfo> fpList = new ArrayList<FPInfo>();
			String itemStr = "", fpStr = "";
			int cur_frequency = 0;

			str = br70.readLine();
			count = 0;
			while (str != null) {
				TermArr = str.split(" ");

				fpStr = "";
				for (int i = 0; i < TermArr.length - 1; i++) {
					itemStr = TermArr[i].replace("item", "");
					item_id = Integer.valueOf(itemStr);
					ItemInfo curItem = IDItemMap.get(item_id);
					frequentPropertySet.add(curItem.Label);
					frequentItemSet.add(item_id);

					fpStr += item_id + " ";
				}
				fpStr = fpStr.trim();
				TermArr[TermArr.length - 1] = TermArr[TermArr.length - 1].substring(1,
						TermArr[TermArr.length - 1].length() - 1);
				cur_frequency = Integer.valueOf(TermArr[TermArr.length - 1]);

				FPInfo newFP = new FPInfo(fpStr, cur_frequency);
				if (fpList.size() > 0 && fpList.get(fpList.size() - 1).isSubFP(newFP)) {
					fpList.get(fpList.size() - 1).FPStr = newFP.FPStr;
					fpList.get(fpList.size() - 1).Frequency = newFP.Frequency;

					while (fpList.size() > 1 && fpList.get(fpList.size() - 2).isSubFP(newFP)) {
						fpList.get(fpList.size() - 2).FPStr = newFP.FPStr;
						fpList.get(fpList.size() - 2).Frequency = newFP.Frequency;

						fpList.remove(fpList.size() - 1);
					}

				} else {
					fpList.add(newFP);
				}

				str = br70.readLine();
			}

			br70.close();

			for (int i = 0; i < fpList.size(); i++) {
				TermArr = fpList.get(i).FPStr.split(" ");

				for (int j = 0; j < TermArr.length; j++) {
					item_id = Integer.valueOf(TermArr[j]);
					if (!ItemFPListMap.containsKey(item_id)) {
						ItemFPListMap.put(item_id, new ArrayList<Integer>());
					}
					ItemFPListMap.get(item_id).add(i);
				}
			}

			Date loadingEndTime = new Date();
			System.out.print("loading time:");
			System.out.println(loadingEndTime.getTime() - loadingStartTime.getTime() + "ms");

			System.out.println("begin to process query...");
			String fileStr = args[1];
			InputStream in1 = new FileInputStream(new File(fileStr));
			Reader inr1 = new InputStreamReader(in1);
			BufferedReader br1 = new BufferedReader(inr1);

			System.out.println(fileStr);
			System.out.println("=========================" + "SPTIndex=========================");
			Date currentTime1 = new Date();

			// 閿熸枻鎷峰彇Query閿熷彨纰夋嫹keyword閿熸枻鎷烽敓琛楋綇鎷烽敓鏂ゆ嫹閿熸彮纰夋嫹閿熸枻鎷烽�夐敓鏂ゆ嫹
			str = br1.readLine();
			TermArr = str.split(";");
			int[][] visited = new int[TermArr.length][NodeNum];
			int[][] candidateDist = new int[TermArr.length][NodeNum];
			int[] b = null;
			NeighborInfo[] TermArr2 = null;
			int keyword_count = 0;

			String[] keywordArr = new String[TermArr.length];
			for (int n = 0; n < keywordArr.length; n++) {
				TermArr[n] = TermArr[n].trim();
				keywordArr[n] = TermArr[n];

				Arrays.fill(visited[n], Integer.MAX_VALUE);
				Arrays.fill(candidateDist[n], Integer.MAX_VALUE);

				HashSet<Integer> visitedRNSet = new HashSet<Integer>();
				TreeSet<HeapNode> candidate = new TreeSet<HeapNode>();
				// ArrayList<Integer> keywordelementslist = new
				// ArrayList<Integer>();

				Hits hits = null;
				Query query = null;
				IndexSearcher searcher = new IndexSearcher(args[0] + "/LuceneIndex");
				Analyzer analyzer = new StandardAnalyzer();
				try {
					QueryParser qp = new QueryParser("body", analyzer);
					query = qp.parse(keywordArr[n]);
				} catch (ParseException e) {
				}
				if (searcher != null) {
					hits = searcher.search(query);

					for (int m = 0; m < hits.length(); m++) {

						String titleStr = hits.doc(m).getField("title").stringValue();
						cur_id = Integer.valueOf(titleStr);

						HeapNode curHeapNode = new HeapNode(cur_id);
						curHeapNode.Dist = 0;
						candidateDist[n][cur_id] = 0;

						candidate.add(curHeapNode);
						keyword_count++;

					}

					while (candidate.size() != 0) {
						HeapNode curHeapNode = candidate.pollFirst();
						visited[n][curHeapNode.VertexID] = curHeapNode.Dist;
						keyword_count++;

						if (RNSet.contains(curHeapNode.VertexID) && !visitedRNSet.contains(curHeapNode.VertexID)
								&& visitedRNSet.size() < updateNum) {

							String aKey = "" + curHeapNode.VertexID;
							DatabaseEntry theKey = new DatabaseEntry(aKey.getBytes("UTF-8"));
							DatabaseEntry theData = new DatabaseEntry();

							if (myDatabase1.get(null, theKey, theData, LockMode.DEFAULT) == OperationStatus.SUCCESS) {

								// Recreate the data String.
								byte[] retData = theData.getData();
								b = byteToInt2(retData);
							}

							visitedRNSet.add(curHeapNode.VertexID);
							for (int k = 0; k < NodeNum; k++) {
								if (b[k] != -1) {
									HeapNode newHeapNode = new HeapNode(k);
									newHeapNode.Dist = b[k] + curHeapNode.Dist;

									if (candidateDist[n][k] != Integer.MAX_VALUE) {
										int cur_dist = candidateDist[n][k];
										if (newHeapNode.Dist < cur_dist) {
											candidateDist[n][k] = newHeapNode.Dist;
										}
									} else {
										candidateDist[n][k] = newHeapNode.Dist;
									}

								}
							}
							continue;
						}

						TermArr2 = adjacentList[curHeapNode.VertexID];
						for (int i = 0; i < TermArr2.length; i++) {

							if (visited[n][TermArr2[i].NeighborID] != Integer.MAX_VALUE)
								continue;

							if (candidateDist[n][TermArr2[i].NeighborID] != Integer.MAX_VALUE) {

								int cur_dist = candidateDist[n][TermArr2[i].NeighborID];
								if (TermArr2[i].Distance + curHeapNode.Dist < cur_dist) {
									HeapNode tempHeapNode = new HeapNode(TermArr2[i].NeighborID);
									tempHeapNode.Dist = cur_dist;
									candidate.remove(tempHeapNode);

									HeapNode newHeapNode = new HeapNode(TermArr2[i].NeighborID);
									newHeapNode.Dist = TermArr2[i].Distance + curHeapNode.Dist;
									candidate.add(newHeapNode);

									candidateDist[n][TermArr2[i].NeighborID] = TermArr2[i].Distance + curHeapNode.Dist;
								}
							} else {
								HeapNode newHeapNode = new HeapNode(TermArr2[i].NeighborID);
								newHeapNode.Dist = TermArr2[i].Distance + curHeapNode.Dist;
								candidate.add(newHeapNode);
								candidateDist[n][TermArr2[i].NeighborID] = TermArr2[i].Distance + curHeapNode.Dist;
							}
						}
					}

				}

			}

			System.out.print("keyword Dijkstra time:");
			Date currentTime2 = new Date();
			System.out.println(currentTime2.getTime() - currentTime1.getTime());

			// 閿熸枻鎷峰彇Query閿熷彨纰夋嫹SPARQL閿熸枻鎷烽敓琛楋綇鎷烽敓鏂ゆ嫹閿熸彮纰夋嫹閿熸枻鎷烽�夐敓鏂ゆ嫹
			// 閿熻姤瀹歋PARQL閿熸枻鎷蜂竴閿熸垝璇濋敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷峰嵏閿熸枻鎷烽敓鏂ゆ嫹鐩撮敓鏂ゆ嫹閿燂拷
			// 閿熸枻鎷烽敓鏂ゆ嫹閿熺獤顖ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹閿熺嫛纭锋嫹閿熸枻鎷烽敓鏂ゆ嫹
			str = br1.readLine();
			ArrayList<String> tpList = ParseSPARQL(str);

			TreeSet<VariableInfo> VariableInfoSet = new TreeSet<VariableInfo>();
			int curSubjectID = 0, curObjectID = 0, curPredicateID = 0, tmp_direction = 0, variableNum = 0;
			String curVarStr = "";

			TreeMap<String, ArrayList<String>> QueryAdjacentList = new TreeMap<String, ArrayList<String>>();
			TreeMap<String, ArrayList<NeighborInfo>> QueryStarPatternMap = new TreeMap<String, ArrayList<NeighborInfo>>();
			ArrayList<String> curList = null;
			HashSet<String> varSet = new HashSet<String>();

			for (int tpIdx = 0; tpIdx < tpList.size(); tpIdx++) {
				str = tpList.get(tpIdx);
				TermArr = str.split("\t");

				curPredicateID = PredicateIDMap.get(TermArr[1]);

				// =========================================================
				tmp_direction = -1;

				if (!(TermArr[0].startsWith("?"))) {
					curSubjectID = EntityIDMap.get(TermArr[0]);
					TermArr2 = adjacentList[curSubjectID];
					tmp_direction = 0;
					curVarStr = TermArr[2];
				} else {
					varSet.add(TermArr[0]);
				}

				if (!(TermArr[2].startsWith("?"))) {
					curObjectID = EntityIDMap.get(TermArr[2]);
					TermArr2 = adjacentList[curObjectID];
					tmp_direction = 1;
					curVarStr = TermArr[0];
				} else {
					varSet.add(TermArr[2]);
				}

				if (tmp_direction == -1) {

					if (!QueryStarPatternMap.containsKey(TermArr[0])) {
						QueryStarPatternMap.put(TermArr[0], new ArrayList<NeighborInfo>());
					}
					NeighborInfo curNeighborInfo1 = new NeighborInfo(TermArr[2].hashCode(), curPredicateID);
					curNeighborInfo1.Direction = 0;
					QueryStarPatternMap.get(TermArr[0]).add(curNeighborInfo1);

					if (!QueryStarPatternMap.containsKey(TermArr[2])) {
						QueryStarPatternMap.put(TermArr[2], new ArrayList<NeighborInfo>());
					}
					NeighborInfo curNeighborInfo = new NeighborInfo(TermArr[0].hashCode(), curPredicateID);
					curNeighborInfo.Direction = 1;
					QueryStarPatternMap.get(TermArr[2]).add(curNeighborInfo);

					// ========================================

					if (QueryAdjacentList.containsKey(TermArr[0])) {
						curList = QueryAdjacentList.get(TermArr[0]);
					} else {
						curList = new ArrayList<String>();
					}
					// 0閿熸枻鎷�?閿熸枻鎷蜂负閿熸枻鎷疯閿熼叺顭掓嫹閿熺掸redicate鎸囬敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹閿燂拷
					curList.add("0\t" + curPredicateID + "\t" + TermArr[2]);
					QueryAdjacentList.put(TermArr[0], curList);

					if (QueryAdjacentList.containsKey(TermArr[2])) {
						curList = QueryAdjacentList.get(TermArr[2]);
					} else {
						curList = new ArrayList<String>();
					}
					// 1閿熸枻鎷�?閿熸枻鎷蜂负閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷烽敓閰殿煉鎷烽敓绲edicate鎸囬敓鏂ゆ嫹璇撮敓锟�
					curList.add("1\t" + curPredicateID + "\t" + TermArr[0]);
					QueryAdjacentList.put(TermArr[2], curList);

					str = br1.readLine();
					continue;
				}

				VariableInfo curVariableInfo = new VariableInfo(curVarStr);
				for (int i = 0; i < TermArr2.length; i++) {
					if (TermArr2[i].Direction == tmp_direction) {
						curVariableInfo.addMatches(TermArr2[i].NeighborID);
					}
				}

				if (VariableInfoSet.contains(curVariableInfo)) {
					VariableInfo tempVariableInfo = VariableInfoSet.floor(curVariableInfo);
					tempVariableInfo.retain(curVariableInfo);
				} else {
					VariableInfoSet.add(curVariableInfo);
				}

				str = br1.readLine();
			}

			variableNum = varSet.size();

			TreeMap<String, TreeSet<String>> varPatternMap = generateVarPatternMap(QueryStarPatternMap, ItemFPListMap,
					fpList, LabelItemPosMap);

			Iterator<Entry<String, TreeSet<String>>> iter_var = varPatternMap.entrySet().iterator();
			while (iter_var.hasNext()) {
				Entry<String, TreeSet<String>> e = iter_var.next();
				curVarStr = e.getKey();
				TreeSet<String> aList = e.getValue();

				Iterator<String> iter_fp = aList.iterator();
				while (iter_fp.hasNext()) {
					fpStr = iter_fp.next() + "\t";

					DatabaseEntry theKey = new DatabaseEntry(fpStr.getBytes("UTF-8"));
					DatabaseEntry theData = new DatabaseEntry();

					if (myDatabase2.get(null, theKey, theData, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
						// Recreate the data String.
						byte[] retData = theData.getData();
						int[] idList = byteToInt2(retData);

						VariableInfo curVariableInfo = new VariableInfo(curVarStr);
						for (int i = 0; i < idList.length; i++) {
							curVariableInfo.addMatches(idList[i]);
						}

						if (VariableInfoSet.contains(curVariableInfo)) {
							VariableInfo tempVariableInfo = VariableInfoSet.floor(curVariableInfo);
							tempVariableInfo.retain(curVariableInfo);
						} else {
							VariableInfoSet.add(curVariableInfo);
						}
					} else {
						System.out.println("No record found for key '" + fpStr + "'.");
					}

				}

			}

			// 閫夊彇selectivity閿熸枻鎷峰彥璋嬮敓鏂ゆ嫹閿燂拷
			System.out.print("candidates number:");

			Iterator<VariableInfo> iter2 = VariableInfoSet.iterator();
			int min = Integer.MAX_VALUE;
			VariableInfo minVariableInfo = null;
			while (iter2.hasNext()) {
				VariableInfo tempVariableInfo = iter2.next();
				System.out.print(tempVariableInfo.VariableStr + " " + tempVariableInfo.size() + ", ");
				if (min > tempVariableInfo.size()) {
					minVariableInfo = tempVariableInfo;
					min = tempVariableInfo.size();
				}
			}
			System.out.println();

			// finding the variable with the highest selectivity and query plan
			int cur_match_pos = 0;
			String[] variableArr = new String[variableNum];
			for (int i = 0; i < variableNum; i++) {
				variableArr[i] = "";
			}

			ArrayList<String> queryEdgeList = new ArrayList<String>();
			LinkedList<String> queryQueue = new LinkedList<String>();
			variableArr[0] = minVariableInfo.VariableStr;
			queryQueue.add(minVariableInfo.VariableStr);
			String curVariableStr = "";
			String curEdgeStr = "";

			while (queryQueue.size() != 0) {
				curVariableStr = queryQueue.pop();
				if (QueryAdjacentList.containsKey(curVariableStr)) {
					curList = QueryAdjacentList.get(curVariableStr);
					for (int i = 0; i < curList.size(); i++) {
						curEdgeStr = curList.get(i);
						TermArr = curEdgeStr.split("\t");
						// 閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹鍊间负-1閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷烽敓鍓挎唻鎷烽敓鏂ゆ嫹
						// 閿熸枻鎷烽敓娲ヨ繑鍥炵鎷峰墠閿熸枻鎷烽敓鎻紮鎷烽敓杞夸紮鎷烽敓锟�
						cur_match_pos = SearchInArray(variableArr, TermArr[2]);
						if (cur_match_pos != -1) {
							queryQueue.add(TermArr[2]);
							variableArr[cur_match_pos] = TermArr[2];
						}
						if (!containsEdge(queryEdgeList, curVariableStr, TermArr)) {
							queryEdgeList.add(curVariableStr + "\t" + curEdgeStr);
						}
					}
				}
			}

			// 閿熺即鐚存嫹閫夐敓鏂ゆ嫹閿熸枻鎷锋嫾閿熸帴绛规嫹閿熸枻鎷烽敓绉告枻鎷蜂箣閿熸枻鎷蜂竴閿熸枻鎷�
			// 閿熸枻鎷峰偦閿熸彮浼欐嫹閿熸枻鎷烽敓鏂ゆ嫹閿燂拷
			// 閿熸枻鎷烽敓鏂ゆ嫹閿熻姤鍒伴敓鏂ゆ嫹閿熺Ц浼欐嫹閿燂拷
			TreeSet<ResultInfo> resultSet = new TreeSet<ResultInfo>();
			ResultInfo tempResultInfo = null;
			ArrayList<ResultInfo> tempResultInfoList = new ArrayList<ResultInfo>();
			Iterator<Integer> iter1 = minVariableInfo.matchesSet.iterator();

			while (iter1.hasNext()) {
				tempResultInfo = new ResultInfo(variableNum, 0, keywordArr.length);
				tempResultInfo.matchArr[0] = iter1.next();

				if (QueryStarPatternMap.containsKey(variableArr[0])) {
					for (int j = 0; j < keywordArr.length; j++) {
						tempResultInfo.scoreArr[j] = candidateDist[j][tempResultInfo.matchArr[0]];
					}
				}

				tempResultInfoList.add(tempResultInfo);
				if (queryEdgeList.size() == 0)
					resultSet.add(tempResultInfo);
			}

			Collections.sort(tempResultInfoList);
			LinkedList<ResultInfo> resultStack = new LinkedList<ResultInfo>(tempResultInfoList);

			// 閿熸枻鎷峰閿熸枻鎷烽敓鏂ゆ嫹鐘舵�侀敓绉哥》鎷烽敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷烽敓鏂ゆ嫹閿熸枻鎷�
			int next_match_pos = 0, cur_socre_bound = Integer.MAX_VALUE;
			int candidate_count = resultStack.size();
			while (resultStack.size() != 0 && queryEdgeList.size() != 0) {

				if (resultSet.size() > TopKNum)
					cur_socre_bound = resultSet.last().getScore();

				tempResultInfo = resultStack.pop();
				if (cur_socre_bound != Integer.MAX_VALUE) {
					if (tempResultInfo.getScore() > cur_socre_bound + 1)
						break;
				}

				curEdgeStr = queryEdgeList.get(tempResultInfo.pos);
				TermArr = curEdgeStr.split("\t");
				curPredicateID = Integer.valueOf(TermArr[2]);
				cur_match_pos = findInArrays(variableArr, TermArr[0]);
				next_match_pos = findInArrays(variableArr, TermArr[3]);
				tmp_direction = Integer.valueOf(TermArr[1]);

				TermArr2 = adjacentList[(tempResultInfo.matchArr[cur_match_pos])];

				// while (rs.next()) {
				for (int i1 = 0; i1 < TermArr2.length; i1++) {
					if (TermArr2[i1].Label != curPredicateID || TermArr2[i1].Direction != tmp_direction)
						continue;

					cur_id = TermArr2[i1].NeighborID;
					if (tempResultInfo.contains(cur_id))
						continue;

					VariableInfo curVariableInfo = new VariableInfo(TermArr[3]);
					if (VariableInfoSet.contains(curVariableInfo)) {
						VariableInfo tempVariableInfo = VariableInfoSet.floor(curVariableInfo);
						if (!tempVariableInfo.matchesSet.contains(cur_id))
							continue;
					}

					if (tempResultInfo.matchArr[next_match_pos] == -1) {
						ResultInfo curResultInfo = new ResultInfo(tempResultInfo, tempResultInfo.pos + 1);
						curResultInfo.matchArr[next_match_pos] = cur_id;

						if (QueryStarPatternMap.containsKey(variableArr[next_match_pos])) {
							for (int i = 0; i < keywordArr.length; i++) {
								int temp_dist = candidateDist[i][cur_id];
								if (temp_dist < curResultInfo.scoreArr[i])
									curResultInfo.scoreArr[i] = candidateDist[i][cur_id];
							}
						}

						if (curResultInfo.pos != queryEdgeList.size()) {
							resultStack.push(curResultInfo);
						} else {
							if (curResultInfo.getScore() < cur_socre_bound || cur_socre_bound == Integer.MAX_VALUE)
								resultSet.add(curResultInfo);
							if (resultSet.size() > TopKNum)
								resultSet.pollLast();
						}
						candidate_count++;
					} else {
						if (tempResultInfo.matchArr[next_match_pos] == cur_id) {
							ResultInfo curResultInfo = new ResultInfo(tempResultInfo, tempResultInfo.pos + 1);
							if (curResultInfo.pos != queryEdgeList.size()) {
								resultStack.push(curResultInfo);
							} else {
								if (curResultInfo.getScore() < cur_socre_bound || cur_socre_bound == Integer.MAX_VALUE)
									resultSet.add(curResultInfo);
								if (resultSet.size() > TopKNum)
									resultSet.pollLast();
							}
							candidate_count++;
						}
					}
				}
			}
			br1.close();

			System.out.print("sparql time:");
			Date currentTime3 = new Date();
			System.out.println(currentTime3.getTime() - currentTime2.getTime());

			System.out.print("total time:");
			System.out.println(currentTime3.getTime() - currentTime1.getTime());

			for (int i = 0; i < variableArr.length; i++)
				System.out.print(variableArr[i] + "\t");
			System.out.println();
			System.out.println(resultSet.size());

			Iterator<ResultInfo> iter = resultSet.iterator();
			while (iter.hasNext()) {
				ResultInfo e = iter.next();
				for (int i = 0; i < e.matchArr.length; i++) {
					System.out.print(IDEntityMap.get(e.matchArr[i]) + "\t");
				}
				System.out.println();
			}

			// System.out.println("candidate count:\t" + candidate_count);
			// System.out.println("keyword count:\t" + keyword_count);

			if (myDatabase1 != null) {
				myDatabase1.close();
			}

			if (myDbEnvironment1 != null) {
				myDbEnvironment1.close();
			}
			if (myDatabase2 != null) {
				myDatabase2.close();
			}

			if (myDbEnvironment2 != null) {
				myDbEnvironment2.close();
			}

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static TreeMap<String, TreeSet<String>> generateVarPatternMap(
			TreeMap<String, ArrayList<NeighborInfo>> QueryStarPatternMap,
			TreeMap<Integer, ArrayList<Integer>> ItemFPListMap, ArrayList<FPInfo> fpList,
			TreeMap<Integer, Integer> LabelItemPosMap) {

		TreeMap<String, TreeSet<String>> VarPatternMap = new TreeMap<String, TreeSet<String>>();

		Iterator<Entry<String, ArrayList<NeighborInfo>>> iter_var = QueryStarPatternMap.entrySet().iterator();
		int label_count = 0;
		while (iter_var.hasNext()) {
			Entry<String, ArrayList<NeighborInfo>> e = iter_var.next();
			String curVarStr = e.getKey();
			ArrayList<NeighborInfo> curAdjacentList = e.getValue();

			Collections.sort(curAdjacentList);
			VarPatternMap.put(curVarStr, new TreeSet<String>());

			label_count = 1;
			for (int i = 0; i < curAdjacentList.size(); i++) {
				if (i > 0) {
					if (curAdjacentList.get(i).Label != curAdjacentList.get(i - 1).Label
							|| curAdjacentList.get(i).Direction != curAdjacentList.get(i - 1).Direction) {

						int tmp_item = LabelItemPosMap.get(curAdjacentList.get(i - 1).Label);
						if (label_count >= 1) {
							if (curAdjacentList.get(i - 1).Direction == 0) {
								if (ItemFPListMap.containsKey(tmp_item)) {
									ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item);
									for (int k = 0; k < tmp_pos_list.size(); k++) {
										VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
									}
								}
							} else {
								if (ItemFPListMap.containsKey(tmp_item + 3)) {
									ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 3);
									for (int k = 0; k < tmp_pos_list.size(); k++) {
										VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
									}
								}
							}
						}
						if (label_count >= 2) {
							if (curAdjacentList.get(i - 1).Direction == 0) {
								if (ItemFPListMap.containsKey(tmp_item + 1)) {
									ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 1);
									for (int k = 0; k < tmp_pos_list.size(); k++) {
										VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
									}
								}
							} else {
								if (ItemFPListMap.containsKey(tmp_item + 4)) {
									ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 4);
									for (int k = 0; k < tmp_pos_list.size(); k++) {
										VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
									}
								}
							}
						}
						if (label_count >= 3) {
							if (curAdjacentList.get(i - 1).Direction == 0) {
								if (ItemFPListMap.containsKey(tmp_item + 2)) {
									ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 2);
									for (int k = 0; k < tmp_pos_list.size(); k++) {
										VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
									}
								}
							} else {
								if (ItemFPListMap.containsKey(tmp_item + 5)) {
									ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 5);
									for (int k = 0; k < tmp_pos_list.size(); k++) {
										VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
									}
								}
							}
						}

						label_count = 1;
					} else {
						label_count++;
					}
				}
			}

			int tmp_item = LabelItemPosMap.get(curAdjacentList.get(curAdjacentList.size() - 1).Label);
			if (label_count >= 1) {
				if (curAdjacentList.get(curAdjacentList.size() - 1).Direction == 0) {
					if (ItemFPListMap.containsKey(tmp_item)) {
						ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item);
						for (int k = 0; k < tmp_pos_list.size(); k++) {
							VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
						}
					}
				} else {
					if (ItemFPListMap.containsKey(tmp_item + 3)) {
						ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 3);
						for (int k = 0; k < tmp_pos_list.size(); k++) {
							VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
						}
					}
				}
			}
			if (label_count >= 2) {
				if (curAdjacentList.get(curAdjacentList.size() - 1).Direction == 0) {
					if (ItemFPListMap.containsKey(tmp_item + 1)) {
						ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 1);
						for (int k = 0; k < tmp_pos_list.size(); k++) {
							VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
						}
					}
				} else {
					if (ItemFPListMap.containsKey(tmp_item + 4)) {
						ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 4);
						for (int k = 0; k < tmp_pos_list.size(); k++) {
							VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
						}
					}
				}
			}
			if (label_count >= 3) {
				if (curAdjacentList.get(curAdjacentList.size() - 1).Direction == 0) {
					if (ItemFPListMap.containsKey(tmp_item + 2)) {
						ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 2);
						for (int k = 0; k < tmp_pos_list.size(); k++) {
							VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
						}
					}
				} else {
					if (ItemFPListMap.containsKey(tmp_item + 5)) {
						ArrayList<Integer> tmp_pos_list = ItemFPListMap.get(tmp_item + 5);
						for (int k = 0; k < tmp_pos_list.size(); k++) {
							VarPatternMap.get(curVarStr).add(fpList.get(tmp_pos_list.get(k)).FPStr);
						}
					}
				}
			}

		}
		return VarPatternMap;
	}

	private static HashMap<String, Integer> CreateIDEntityMap(String fileStr, HashMap<Integer, String> IDVertexMap)
			throws IOException {
		String str = "";
		String[] TermArr;
		int cur_id = 0;

		InputStream in0 = new FileInputStream(new File(fileStr));
		Reader inr0 = new InputStreamReader(in0);
		BufferedReader br0 = new BufferedReader(inr0);

		HashMap<String, Integer> VertexIDMap = new HashMap<String, Integer>();
		IDVertexMap = new HashMap<Integer, String>();
		str = br0.readLine();

		while (str != null) {
			// if (count % 10000 == 0)
			// System.out.println(count);
			// count++;

			TermArr = str.split("\t");
			cur_id = Integer.valueOf(TermArr[0]);
			VertexIDMap.put(TermArr[1], cur_id);
			IDVertexMap.put(cur_id, TermArr[1]);

			str = br0.readLine();
		}

		br0.close();

		return VertexIDMap;
	}

	private static int findInArrays(String[] variableArr, String str) {
		for (int i = 0; i < variableArr.length; i++) {
			if (variableArr[i].equals(str)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean containsEdge(ArrayList<String> res_edge_list, String curVariableStr, String[] termArr) {
		String str = "";

		for (int i = 0; i < res_edge_list.size(); i++) {
			str = res_edge_list.get(i);
			String[] curTermArr = str.split("\t");

			if (curTermArr[0].equals(termArr[2]) && curTermArr[3].equals(curVariableStr)
					&& curTermArr[2].equals(termArr[1])) {
				return true;
			}
		}

		return false;
	}

	private static int SearchInArray(String[] variableArr, String str) {
		for (int i = 0; i < variableArr.length; i++) {
			if (variableArr[i].equals(str)) {
				return -1;
			}
			if (variableArr[i].equals("")) {
				return i;
			}
		}
		return -2;
	}

	private static byte[] intArrayToBytes(int[] a) {
		byte[] b = new byte[a.length + a.length + a.length + a.length];
		int offset = 8, pos = 0;

		for (int i = 0; i < a.length; i++) {
			offset = 0;
			pos = i + i + i + i;
			for (int j = 0; j < 4; j++) {
				b[pos + j] = (byte) (a[i] >> (24 - offset));
				offset += 8;
			}
		}

		return b;
	}

	private static int[] byteToInt2(byte[] b) {
		int mask = 0xff, temp = 0;
		int[] n = new int[b.length / 4];

		for (int j = 0; j < n.length; j++) {
			int offset = j + j + j + j;
			for (int i = 0; i < 4; i++) {
				n[j] <<= 8;
				temp = b[offset + i] & mask;
				n[j] |= temp;
			}
		}

		return n;
	}

	private static ArrayList<String> ParseSPARQL(String queryString) {

		ArrayList<String> tpList = new ArrayList<String>();

		try {
			SPARQLParser parser = new SPARQLParser();
			ParsedQuery query = parser.parseQuery(queryString, null);

			StatementPatternCollector collector = new StatementPatternCollector();
			query.getTupleExpr().visit(collector);

			List<StatementPattern> patterns = collector.getStatementPatterns();

			for (int i = 0; i < patterns.size(); i++) {
				StatementPattern curPattern = patterns.get(i);
				tpList.add(TP2String(curPattern));
			}

		} catch (MalformedQueryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return tpList;
	}

	private static String TP2String(StatementPattern curPattern) {
		String curTriplePatternStr = "";

		if (!curPattern.getSubjectVar().isConstant()) {
			curTriplePatternStr += "?" + curPattern.getSubjectVar().getName() + "\t";
		} else {
			curTriplePatternStr += "<" + curPattern.getSubjectVar().getValue().toString() + ">\t";
		}

		if (!curPattern.getPredicateVar().isConstant()) {
			curTriplePatternStr += "?" + curPattern.getPredicateVar().getName() + "\t";
		} else {
			curTriplePatternStr += "<" + curPattern.getPredicateVar().getValue().toString() + ">\t";
		}

		if (!curPattern.getObjectVar().isConstant()) {
			curTriplePatternStr += "?" + curPattern.getObjectVar().getName() + "\t";
		} else {

			if (!curPattern.getObjectVar().getValue().toString().startsWith("\"")) {
				curTriplePatternStr += "<" + curPattern.getObjectVar().getValue().toString() + ">\t";
			} else {
				curTriplePatternStr += curPattern.getObjectVar().getValue().toString();
			}
		}
		curTriplePatternStr = curTriplePatternStr.replace("^^<http://www.w3.org/2001/XMLSchema#string>", "");
		curTriplePatternStr = curTriplePatternStr.trim();

		return curTriplePatternStr;
	}
}
