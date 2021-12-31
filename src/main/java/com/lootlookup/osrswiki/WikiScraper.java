package com.lootlookup.osrswiki;

import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import com.lootlookup.utils.Constants;
import okhttp3.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class WikiScraper {
    private final static String baseUrl = "https://oldschool.runescape.wiki";
    private final static String baseWikiUrl = baseUrl + "/w/";

    public static OkHttpClient client = new OkHttpClient();
    private static Document doc;

    public static CompletableFuture<Map<DropTableType, WikiItem[]>> getDropsByMonsterName(String monsterName) {
        CompletableFuture<Map<DropTableType, WikiItem[]>> future = new CompletableFuture<>();

        String url = getWikiUrl(monsterName);

        requestAsync(url).whenCompleteAsync((responseHTML, ex) -> {
            Map<DropTableType, WikiItem[]> dropTables = new LinkedHashMap<>();

            if (ex != null) {
                future.complete(dropTables);
            }

            doc = Jsoup.parse(responseHTML);
            Elements tableHeaders = doc.select("h3 span.mw-headline");

            Elements validTableHeaders = new Elements();

            for (Element tableHeader : tableHeaders) {
                DropTableType tableType = parseTableType(tableHeader.text());
                if (tableType != null) {
                    validTableHeaders.add(tableHeader);
                }

            }

            int tableIndex = 0;
            for (Element validTableHeader : validTableHeaders) {
                DropTableType tableType = parseTableType(validTableHeader.text());
                if (tableType != null) {
                    WikiItem[] tableRows = getTableItems(tableIndex);

                    if (tableRows.length > 0) {
                        dropTables.put(tableType, tableRows);
                    }
                }
                tableIndex++;
            }

            future.complete(dropTables);
        });

        return future;
    }


    private static WikiItem[] getTableItems(int tableIndex) {
        List<WikiItem> wikiItems = new ArrayList<>();
        Elements dropTables = doc.select("h3 ~ table.item-drops");

        if (dropTables.size() > tableIndex) {
            Elements dropTableRows = dropTables.get(tableIndex).select("tbody tr");
            for (Element dropTableRow : dropTableRows) {
                String[] lootRow = new String[5];
                Elements dropTableCells = dropTableRow.select("td");
                int index = 1;

                for (Element dropTableCell : dropTableCells) {
                    String cellContent = dropTableCell.text();
                    Elements images = dropTableCell.select("img");

                    if (images.size() != 0) {
                        String imageSource = images.first().attr("src");
                        if (!imageSource.isEmpty()) {
                            lootRow[0] = baseUrl + imageSource;
                        }
                    }

                    if (cellContent != null && !cellContent.isEmpty() && index < 5) {
                        cellContent = filterTableContent(cellContent);
                        lootRow[index] = cellContent;
                        index++;
                    }
                }

                if (lootRow[0] != null) {
                    WikiItem wikiItem = parseRow(lootRow);
                    wikiItems.add(wikiItem);
                }
            }
        }


        WikiItem[] result = new WikiItem[wikiItems.size()];
        return wikiItems.toArray(result);
    }

    public static DropTableType parseTableType(String tableHeader) {
        for (DropTableType tableType : DropTableType.values()) {
            if (tableHeader.equalsIgnoreCase(String.valueOf(tableType))) {
                return tableType;
            }
        }
        return null;
    }

    public static WikiItem parseRow(String[] row) {
        String imageUrl = "";
        String name = "";

        double rarity = -1;
        String rarityStr = "";

        int quantity = 0;
        int price = -1;

        if (row.length > 4) {
            imageUrl = row[0];
            name = row[1];

            NumberFormat nf = NumberFormat.getNumberInstance();

            try {
                quantity = nf.parse(row[2]).intValue();
            } catch (ParseException e) {
            }

            rarityStr = row[3];


            try {
                String[] rarityStrs = rarityStr.replaceAll("\\s+", "").split(";");
                String firstRarityStr = rarityStrs.length > 0 ? rarityStrs[0] : null;

                if (firstRarityStr != null) {
                    if (firstRarityStr.equals("Always")) {
                        rarity = 1.0;
                    } else {
                        String[] fraction = firstRarityStr.split("/");
                        if (fraction.length > 1) {
                            double numer = nf.parse(fraction[0]).doubleValue();
                            double denom = nf.parse(fraction[1]).doubleValue();
                            rarity = numer / denom;
                        }

                    }
                }
            } catch (ParseException ex) {
            }


            try {
                price = nf.parse(row[4]).intValue();
            } catch (ParseException ex) {
            }
        }
        return new WikiItem(imageUrl, name, quantity, rarityStr, rarity, price);
    }


    public static String filterTableContent(String cellContent) {
        return cellContent.replaceAll("\\[.*\\]", "");
    }

    public static String getWikiUrl(String itemOrMonsterName) {
        String sanitizedName = sanitizeMonsterName(itemOrMonsterName);
        return baseWikiUrl + sanitizedName;
    }

    public static String getWikiUrlForDrops(String monsterName) {
        String sanitizedMonsterName = sanitizeMonsterName(monsterName);
        return baseWikiUrl + sanitizedMonsterName + "#Drops";
    }

    public static String sanitizeMonsterName(String monsterName) {
        monsterName = monsterName.strip().toLowerCase().replaceAll("\\s+", "_");
        return monsterName.substring(0, 1).toUpperCase() + monsterName.substring(1);
    }

    private static CompletableFuture<String> requestAsync(String url) {
        CompletableFuture<String> future = new CompletableFuture<>();

        Request request = new Request.Builder().url(url).header("User-Agent", Constants.USER_AGENT).build();

        client
                .newCall(request)
                .enqueue(
                        new Callback() {
                            @Override
                            public void onFailure(Call call, IOException ex) {
                                future.completeExceptionally(ex);
                            }

                            @Override
                            public void onResponse(Call call, Response response) throws IOException {
                                try (ResponseBody responseBody = response.body()) {
                                    if (!response.isSuccessful()) future.complete("");

                                    future.complete(responseBody.string());
                                } finally {
                                    response.close();
                                }
                            }
                        });

        return future;
    }

}