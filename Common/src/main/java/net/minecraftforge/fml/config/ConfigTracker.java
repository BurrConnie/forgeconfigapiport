/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.mojang.logging.LogUtils;
import fuzs.forgeconfigapiport.impl.core.CommonAbstractions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigTracker {
    private static final Logger LOGGER = LogUtils.getLogger();
    static final Marker CONFIG = MarkerFactory.getMarker("CONFIG");
    public static final ConfigTracker INSTANCE = new ConfigTracker();
    private final ConcurrentHashMap<String, ModConfig> fileMap;
    private final EnumMap<ModConfig.Type, Set<ModConfig>> configSets;
    // Forge Config API Port: store a collection of mod configs since mods with multiple configs for the same type are supported
    private final ConcurrentHashMap<String, Map<ModConfig.Type, Collection<ModConfig>>> configsByMod;

    private ConfigTracker() {
        this.fileMap = new ConcurrentHashMap<>();
        this.configSets = new EnumMap<>(ModConfig.Type.class);
        this.configsByMod = new ConcurrentHashMap<>();
        this.configSets.put(ModConfig.Type.CLIENT, Collections.synchronizedSet(new LinkedHashSet<>()));
        this.configSets.put(ModConfig.Type.COMMON, Collections.synchronizedSet(new LinkedHashSet<>()));
//        this.configSets.put(ModConfig.Type.PLAYER, new ConcurrentSkipListSet<>());
        this.configSets.put(ModConfig.Type.SERVER, Collections.synchronizedSet(new LinkedHashSet<>()));
    }

    void trackConfig(final ModConfig config) {
        if (this.fileMap.containsKey(config.getFileName())) {
            LOGGER.error(CONFIG,"Detected config file conflict {} between {} and {}", config.getFileName(), this.fileMap.get(config.getFileName()).getModId(), config.getModId());
            throw new RuntimeException("Config conflict detected!");
        }
        this.fileMap.put(config.getFileName(), config);
        this.configSets.get(config.getType()).add(config);
        // Forge Config API Port: store a collection of mod configs since mods with multiple configs for the same type are supported
        this.configsByMod.computeIfAbsent(config.getModId(), (k)->new EnumMap<>(ModConfig.Type.class)).computeIfAbsent(config.getType(), type -> new ArrayList<>()).add(config);
        LOGGER.debug(CONFIG, "Config file {} for {} tracking", config.getFileName(), config.getModId());
        loadTrackedConfig(config);  // Forge Config API Port: load configs immediately
    }

    // Forge Config API Port: additional method for loading a single config immediately
    private void loadTrackedConfig(ModConfig config) {
        // unlike on forge there isn't really more than one loading stage for mods on fabric, therefore we load configs immediately
        if (config.getType() == ModConfig.Type.CLIENT) {
            openConfig(config, CommonAbstractions.INSTANCE.getClientConfigDirectory());
        } else if (config.getType() == ModConfig.Type.COMMON) {
            openConfig(config, CommonAbstractions.INSTANCE.getCommonConfigDirectory());
        }
        // server configs are not handled here, they are all loaded at once when a world is loaded
    }

    public void loadConfigs(ModConfig.Type type, Path configBasePath) {
        LOGGER.debug(CONFIG, "Loading configs type {}", type);
        this.configSets.get(type).forEach(config -> openConfig(config, configBasePath));
    }

    public void unloadConfigs(ModConfig.Type type, Path configBasePath) {
        LOGGER.debug(CONFIG, "Unloading configs type {}", type);
        this.configSets.get(type).forEach(config -> closeConfig(config, configBasePath));
    }

    private void openConfig(final ModConfig config, final Path configBasePath) {
        LOGGER.trace(CONFIG, "Loading config file type {} at {} for {}", config.getType(), config.getFileName(), config.getModId());
        final CommentedFileConfig configData = config.getHandler().reader(configBasePath).apply(config);
        config.setConfigData(configData);
        // Forge Config API Port: invoke Fabric style callback instead of Forge event
        CommonAbstractions.INSTANCE.fireConfigLoading(config.getModId(), config);
        config.save();
    }

    private void closeConfig(final ModConfig config, final Path configBasePath) {
        if (config.getConfigData() != null) {
            LOGGER.trace(CONFIG, "Closing config file type {} at {} for {}", config.getType(), config.getFileName(), config.getModId());
            // stop the filewatcher before we save the file and close it, so reload doesn't fire
            config.getHandler().unload(configBasePath, config);
            CommonAbstractions.INSTANCE.fireConfigUnloading(config.getModId(), config);
            config.save();
            config.setConfigData(null);
        }
    }

    public void loadDefaultServerConfigs() {
        configSets.get(ModConfig.Type.SERVER).forEach(modConfig -> {
            final CommentedConfig commentedConfig = CommentedConfig.inMemory();
            modConfig.getSpec().correct(commentedConfig);
            modConfig.setConfigData(commentedConfig);
            // Forge Config API Port: invoke Fabric style callback instead of Forge event
            CommonAbstractions.INSTANCE.fireConfigLoading(modConfig.getModId(), modConfig);
        });
    }

    @Nullable
    public String getConfigFileName(String modId, ModConfig.Type type) {
        // Forge Config API Port: support mods with multiple configs for the same type
        List<String> fileNames = this.getConfigFileNames(modId, type);
        return fileNames.isEmpty() ? null : fileNames.get(0);
    }

    // Forge Config API Port: support mods with multiple configs for the same type, does not exist on Forge, therefore marked as internal
    // It's ok to use this in a Fabric/Quilt project, just don't use it in Common, that's what the annotation is for
    @ApiStatus.Internal
    public List<String> getConfigFileNames(String modId, ModConfig.Type type) {
        return Optional.ofNullable(this.configsByMod.get(modId))
                .map(map -> map.get(type))
                .map(configs -> configs.stream()
                        .filter(config -> config.getConfigData() instanceof FileConfig)
                        .map(ModConfig::getFullPath)
                        .map(Object::toString)
                        .toList())
                .orElse(List.of());
    }

    public Map<ModConfig.Type, Set<ModConfig>> configSets() {
        return configSets;
    }

    public ConcurrentHashMap<String, ModConfig> fileMap() {
        return fileMap;
    }
}
