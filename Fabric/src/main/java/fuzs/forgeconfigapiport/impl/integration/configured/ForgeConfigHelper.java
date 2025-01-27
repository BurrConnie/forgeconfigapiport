/*
 * Copyright (c) MrCrayfish
 * SPDX-License-Identifier: GPLv3
 */

package fuzs.forgeconfigapiport.impl.integration.configured;

import com.electronwill.nightconfig.core.AbstractConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.google.common.collect.ImmutableList;
import fuzs.forgeconfigapiport.impl.util.ReflectionHelperV2;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: MrCrayfish
 */
public class ForgeConfigHelper
{
    /**
     * Gathers all the Forge config values with a deep search. Used for resetting defaults
     */
    public static List<Pair<ForgeConfigSpec.ConfigValue<?>, ForgeConfigSpec.ValueSpec>> gatherAllForgeConfigValues(UnmodifiableConfig config, ForgeConfigSpec spec)
    {
        List<Pair<ForgeConfigSpec.ConfigValue<?>, ForgeConfigSpec.ValueSpec>> values = new ArrayList<>();
        gatherValuesFromForgeConfig(config, spec, values);
        return ImmutableList.copyOf(values);
    }

    /**
     * Gathers all the config values from the given Forge config and adds it's to the provided list.
     * This will search deeper if it finds another config and recursively call itself.
     */
    private static void gatherValuesFromForgeConfig(UnmodifiableConfig config, ForgeConfigSpec spec, List<Pair<ForgeConfigSpec.ConfigValue<?>, ForgeConfigSpec.ValueSpec>> values)
    {
        config.valueMap().forEach((s, o) ->
        {
            if(o instanceof AbstractConfig)
            {
                gatherValuesFromForgeConfig((UnmodifiableConfig) o, spec, values);
            }
            else if(o instanceof ForgeConfigSpec.ConfigValue<?> configValue)
            {
                ForgeConfigSpec.ValueSpec valueSpec = spec.getRaw(configValue.getPath());
                values.add(Pair.of(configValue, valueSpec));
            }
        });
    }

    /**
     * Since ModConfig#setConfigData is not visible, this is a helper method to reflectively call the method
     *
     * @param config     the config to update
     * @param configData the new data for the config
     */
    public static void setForgeConfigData(ModConfig config, @Nullable CommentedConfig configData)
    {
        // Forge Config API Port: replace Forge's ObfuscationReflectionHelper with custom ReflectionHelper implementation
        ReflectionHelperV2.invokeMethod(ModConfig.class, "setConfigData", new Class[]{CommentedConfig.class}, config, new Object[]{configData});
        if(configData instanceof FileConfig)
        {
            config.save();
        }
    }

    /**
     * Gathers all the config values with a deep search. Used for resetting defaults
     */
    public static List<Pair<ForgeConfigSpec.ConfigValue<?>, ForgeConfigSpec.ValueSpec>> gatherAllForgeConfigValues(ModConfig config)
    {
        return gatherAllForgeConfigValues(((ForgeConfigSpec) config.getSpec()).getValues(), (ForgeConfigSpec) config.getSpec());
    }
}
