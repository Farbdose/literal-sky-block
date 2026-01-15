package dev.latvian.mods.literalskyblock.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class IrisCompat {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Field PIPELINE;
	private static final String IRIS_CLASS = "net.coderbot.iris.Iris";
	private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";
	private static final String WORLD_RENDERING_PHASE_CLASS = "net.coderbot.iris.pipeline.WorldRenderingPhase";

	static {
		Field pipeline;
		try {
			//noinspection JavaReflectionMemberAccess
			pipeline = LevelRenderer.class.getDeclaredField("pipeline");
			pipeline.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			pipeline = null;
			LOGGER.error("Failed to get Iris pipeline field", e);
		}
		PIPELINE = pipeline;
	}

	public static void preRender(LevelRenderer renderer) {
		if (PIPELINE == null) {
			return;
		}
		try {
			Class<?> irisClass = Class.forName(IRIS_CLASS);
			Object pipelineManager = irisClass.getMethod("getPipelineManager").invoke(null);
			Object currentDimension = irisClass.getMethod("getCurrentDimension").invoke(null);
			Method preparePipeline = pipelineManager.getClass().getMethod("preparePipeline", currentDimension.getClass());
			Object pipeline = preparePipeline.invoke(pipelineManager, currentDimension);
			PIPELINE.set(renderer, pipeline);
			//pipeline.beginLevelRendering();
			Class<?> phaseClass = Class.forName(WORLD_RENDERING_PHASE_CLASS);
			Object nonePhase = phaseClass.getField("NONE").get(null);
			pipeline.getClass().getMethod("setOverridePhase", phaseClass).invoke(pipeline, nonePhase);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.error("Exception in preRender", e);
		}
	}

	public static void postRender(LevelRenderer renderer) {
		if (PIPELINE == null) {
			return;
		}
		try {
			Object pipeline = PIPELINE.get(renderer);
			//pipeline.finalizeLevelRendering();
			PIPELINE.set(renderer, null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.error("Exception in postRender", e);
		}
	}

	public static boolean shadersEnabled() {
		try {
			Class<?> irisApiClass = Class.forName(IRIS_API_CLASS);
			Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
			Object enabled = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
			return enabled instanceof Boolean && (Boolean) enabled;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Failed to query Iris shader state", e);
			return false;
		}
	}
}
