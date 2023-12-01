package org.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Setter
@Getter
public class Main {
    public static final Gson gson = new Gson();
    private static final String FILE_PATH = "config.txt";
    private int defaultBPPercent;

    private int donateBPPercent;
    Map<String, Map<String, Object>> cosmetics = new HashMap<>();
    private final Random random = new Random(System.nanoTime());
    private final List<String> alreadyUsed = new ArrayList<>();

    public static void main(String[] args) {
        Main main = new Main();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject json = new JsonObject();
        JsonObject columnData = new JsonObject();


        main.setDefaultBPPercent(main.readRareChance("Chance","Default"));
        main.setDonateBPPercent(main.readRareChance("Chance","Donate"));
        Rarity.COMMON.setProbability(main.readRareChance("Rarity","COMMON"));
        Rarity.RARE.setProbability(main.readRareChance("Rarity","RARE"));
        Rarity.EPIC.setProbability(main.readRareChance("Rarity","EPIC"));
        Rarity.LEGENDARY.setProbability(main.readRareChance("Rarity","LEGENDARY"));
        Map<String, String> dataMap = main.readDataFromFile();
        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            Map<String, Object> JsonMap = main.getCandleDataAsync(entry.getValue()).join();
            main.cosmetics.put(entry.getKey(), JsonMap);
        }

//        for (Map.Entry<String, Map<String, Object>> entry : main.cosmetics.entrySet()) {
//            String playerName = entry.getKey();
//            Map<String, Object> playerCosmetics = entry.getValue();
//
//            System.out.println("Type: " + playerName);
//            System.out.println("Cosmetics: " + playerCosmetics);
//            System.out.println();
//        }

        for (int i = 1; i <= 10; i++) {
            columnData.add(String.valueOf(i), main.getColumnData(i));
        }
        json.add("columnData", columnData);

        try (FileWriter file = new FileWriter("output.json")) {
            gson.toJson(json, file);
            System.out.println("JSON файл успешно создан.");
        } catch (IOException e) {
            e.printStackTrace();
        }
        boolean hasDuplicates = hasDuplicates(main.alreadyUsed);
        System.out.println(hasDuplicates);
    }


    // Метод для проверки дубликатов в списке
    private static boolean hasDuplicates(List<String> list) {
        Set<String> set = new HashSet<>();

        for (String element : list) {
            if (!set.add(element)) {
                // Элемент уже был добавлен, значит, есть дубликат
                return true;
            }
        }

        // Все элементы уникальны
        return false;
    }
    private Map<String, String> readDataFromFile() {
        Map<String, String> dataMap = new HashMap<>();

        try {
            Path path = Path.of(FILE_PATH);
            if (!Files.exists(path)) {
                Files.createFile(path);
            }

            BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(": ");
                if (parts.length == 2 && parts[0].startsWith("URL" + ".")) {
                    dataMap.put(parts[0].replace("URL" + ".",""), parts[1]);
                }
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return dataMap;
    }

    private int readRareChance(String category,String need){
        int chance = 0;
        try {
            Path path = Path.of(FILE_PATH);
            if (!Files.exists(path)) {
                Files.createFile(path);
            }

            BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(": ");
                if (parts.length == 2 && parts[0].startsWith(category + "." + need)){
                    chance = Integer.parseInt(parts[1]);
                    break;
                }
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return chance;
    }

    public CompletableFuture<Map<String, Object>> getCandleDataAsync(String url) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        java.util.concurrent.CompletableFuture<Map<String, Object>> completableFuture = new CompletableFuture<>();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                completableFuture.completeExceptionally(e);
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    completableFuture.completeExceptionally(new IOException("Unexpected code " + response));
                    response.close();
                    return;
                }

                try (ResponseBody responseBody = response.body()) {
                    if (responseBody != null) {
                        String responseString = responseBody.string();
                        Type type = new TypeToken<Map<String, Object>>() {
                        }.getType();
                        Map<String, Object> newData = gson.fromJson(responseString, type);

                        Iterator<Map.Entry<String, Object>> iterator = newData.entrySet().iterator();

                        while (iterator.hasNext()) {
                            Map.Entry<String, Object> entry = iterator.next();
                            Map<String, Object> data = (Map<String, Object>) entry.getValue();

                            if (data.containsKey("priceCoins") && data.containsKey("priceDonate")) {
                                int priceCoins = Integer.parseInt(data.get("priceCoins").toString());
                                int priceDonate = Integer.parseInt(data.get("priceDonate").toString());

                                if (priceCoins == 0 && priceDonate == 0) {
                                    iterator.remove();
                                }
                            }
                        }
                        completableFuture.complete(newData);
                    } else {
                        completableFuture.complete(null);
                    }
                }
            }
        });

        return completableFuture;
    }

    private JsonObject getColumnData(int k) {
        JsonObject columnData = new JsonObject();

        for (int i = 1; i <= 10; i++) {
            columnData.add(String.valueOf(i), getRowData(i, k));
        }

        return columnData;
    }

    private final Map<CosmeticType, Integer> typeCounter = new HashMap<>();
    private CosmeticType lastCosmeticType;

    private boolean kostilb = false;
    private JsonObject processCosmeticType(CosmeticType cosmeticType, int k, int j, String type) {
        String randomKey = "";
        String randomInnerKey = "";

        if (kostilb && lastCosmeticType == cosmeticType){
            CosmeticType newCosmeticType = (type.equals("default")) ? getCosmeticTypeForDefault() : getCosmeticTypeForDonate();

            System.out.println(type + " страница: " + j + " пункт: " + k + " КОСТЫЛЬ");
            System.out.println(newCosmeticType.name());
            return processCosmeticType(newCosmeticType, k, j, type);
        }
        typeCounter.compute(cosmeticType, (key, count) -> (count == null) ? 1 : count + 1);

        if (!typeCounter.isEmpty() && typeCounter.get(cosmeticType) == 2) {
            CosmeticType newCosmeticType = (type.equals("default")) ? getCosmeticTypeForDefault() : getCosmeticTypeForDonate();
            typeCounter.clear();
            System.out.println(type + " страница: " + j + " пункт: " + k + " 2 raza podryat");
            System.out.println(newCosmeticType.name());
            kostilb = true;
            return processCosmeticType(newCosmeticType, k, j, type);
        } else {
            if (typeCounter.keySet().stream().anyMatch(c -> c != cosmeticType)) {
                typeCounter.clear();
            }

            kostilb = false;
            System.out.println(type + " страница: " + j + " пункт: " + k + " " + cosmeticType);

            switch (cosmeticType) {
                case COSMETIC -> {
                    while (true) {
                        Object[] keysArray = cosmetics.keySet().toArray();
                        randomKey = (String) keysArray[random.nextInt(keysArray.length)];
                        System.out.println(randomKey);
                        Map<String, Object> innerMap = cosmetics.get(randomKey);

                        if (innerMap != null && !innerMap.isEmpty()) {
                            int totalProbability = innerMap.values().stream()
                                    .mapToInt(entry -> {
                                        Map<String, Object> data = (Map<String, Object>) entry;
                                        return data.containsKey("rare") ? Rarity.valueOf(data.get("rare").toString()).getProbability() : 0;
                                    })
                                    .sum();
                            int randomProbability = random.nextInt(totalProbability);

                            for (Map.Entry<String, Object> entry : innerMap.entrySet()) {
                                Map<String, Object> data = (Map<String, Object>) entry.getValue();
                                if (data.containsKey("rare")) {
                                    Rarity itemRarity = Rarity.valueOf(data.get("rare").toString());
                                    randomProbability -= itemRarity.getProbability();
                                    if (randomProbability <= 0) {
                                        randomInnerKey = entry.getKey() + Rarity.valueOf(data.get("rare").toString());
                                        System.out.println(entry.getKey() + Rarity.valueOf(data.get("rare").toString()));
                                        break;
                                    }
                                }
                            }
                            if (!alreadyUsed.contains(randomInnerKey)) {
                                alreadyUsed.add(randomInnerKey);
                                break;
                            }
                        } else {
                            break;
                        }
                    }
                }
                case MONEY -> {
                    randomKey = "MONEY";
                    randomInnerKey = String.valueOf((20 + random.nextInt(k * j)) * getMoneyMultiplier(type) + random.nextInt(getMoneyMultiplier(type)));
                }
                case BP -> {
                    randomKey = "BP";
                    randomInnerKey = String.valueOf(Math.round((3 + random.nextInt((int) Math.sqrt(k * j))) * (getMoneyMultiplier(type) / 2) + random.nextInt(getLevelBPMultiplier(type))));
                }
                case LEVEL -> {
                    randomKey = "LEVEL";
                    randomInnerKey = String.valueOf(Math.round((7 + random.nextInt((int) Math.sqrt(k * j))) * (getMoneyMultiplier(type) / 1.5) + random.nextInt(getLevelBPMultiplier(type))));
                }
                case NOTHING -> {

                }
            }

            lastCosmeticType = cosmeticType;
            JsonObject value = new JsonObject();
            value.addProperty("cosmeticType", randomKey);
            value.addProperty("donate", randomInnerKey);

            return value;
        }
    }

    private int getMoneyMultiplier(String cosmeticType) {
        return (cosmeticType.equals("default")) ? 100 : 230;
    }

    private int getLevelBPMultiplier(String cosmeticType) {
        return (cosmeticType.equals("default")) ? 50 : 100;
    }

    private JsonObject getRowData(int k, int j) {
        JsonObject rowData = new JsonObject();

        CosmeticType defaultCosmeticType = getCosmeticTypeForDefault();
        JsonObject defaultValue = processCosmeticType(defaultCosmeticType, k, j, "default");

        CosmeticType donateCosmeticType = getCosmeticTypeForDonate();
        JsonObject donateValue = processCosmeticType(donateCosmeticType, k, j, "donate");

        rowData.add("defaultValue", defaultValue);
        rowData.add("donateValue", donateValue);

        return rowData;
    }

    public CosmeticType getCosmeticTypeForDefault() {
        return getCosmeticType(getDefaultBPPercent());
    }

    public CosmeticType getCosmeticTypeForDonate() {
        return getCosmeticType(getDonateBPPercent());
    }

    private CosmeticType getCosmeticType(double probability) {
        double randomValue = Math.random() * 100;
        if (randomValue < probability) {
            CosmeticType[] values = CosmeticType.values();
            int arrayLength = values.length - 1;
            int randomIndex = random.nextInt(arrayLength);
            if (randomIndex >= CosmeticType.NOTHING.ordinal()) {
                randomIndex++;
            }
            return values[randomIndex];
        }else {
            return CosmeticType.NOTHING;
        }
    }

    enum CosmeticType {
        COSMETIC,
        MONEY,
        LEVEL,
        BP,
        NOTHING
    }

    enum Rarity {
        COMMON,
        RARE,
        EPIC,
        LEGENDARY;

        @Getter
        @Setter
        private int probability;
    }
}
