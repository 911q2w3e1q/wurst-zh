package com.github.bunnyi116.bedrockminer.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

public class MyConfig {
    public final int range;
    public final String dimension;
    public final BlockPos origin;

    public MyConfig(int range, String dimension, BlockPos origin) {
        this.range = range;
        this.dimension = dimension;
        this.origin = origin;
    }

    public static final Codec<MyConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("range", 8).forGetter(c -> c.range),
            Codec.STRING.optionalFieldOf("dimension", "minecraft:overworld").forGetter(c -> c.dimension),
            BlockPos.CODEC.optionalFieldOf("origin", new BlockPos(0, 64, 0)).forGetter(c -> c.origin)
    ).apply(instance, MyConfig::new));


//    public static MyConfig load(Path path) {
//        if (Files.notExists(path)) {
//            MyConfig def = defaultConfig();
//            save(path, def);
//            return def;
//        }
//
//        try (Reader reader = Files.newBufferedReader(path)) {
//            JsonElement json = JsonParser.parseReader(reader);
//            return MyConfig.CODEC
//                    .parse(JsonOps.INSTANCE, json)
//                    .getOrThrow(error -> new RuntimeException("Failed to parse config: " + error));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }


}
