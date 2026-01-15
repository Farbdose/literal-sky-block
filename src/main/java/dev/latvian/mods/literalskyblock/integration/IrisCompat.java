package dev.latvian.mods.literalskyblock.integration;

import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.LevelRenderer;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class IrisCompat {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String IRIS_CLASS_NAME = "net.irisshaders.iris.Iris";
	private static final String IRIS_API_CLASS_NAME = "net.irisshaders.iris.api.v0.IrisApi";
	private static final String WORLD_RENDERING_PHASE_CLASS_NAME = "net.irisshaders.iris.pipeline.WorldRenderingPhase";
	private static final Field PIPELINE;

	static {
		Field pipeline;
		try {
			//noinspection JavaReflectionMemberAccess
			pipeline = LevelRenderer.class.getDeclaredField("pipeline");
			pipeline.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			pipeline = null;
			LOGGER.warn("Failed to get Iris pipeline field", e);
		}
		PIPELINE = pipeline;
	}

	public static boolean preRender(LevelRenderer renderer) {
		if (!shadersEnabled()) {
			return false;
		}
		if (PIPELINE == null) {
			LOGGER.warn("Iris pipeline field missing; skipping Iris preRender.");
			return false;
		}
		try {
			Class<?> irisClass = Class.forName(IRIS_CLASS_NAME);
			Object pipelineManager = irisClass.getMethod("getPipelineManager").invoke(null);
			Object currentDimension = irisClass.getMethod("getCurrentDimension").invoke(null);
			if (currentDimension == null) {
				LOGGER.warn("Iris current dimension was null; skipping Iris preRender.");
				return false;
			}
			Method preparePipeline = pipelineManager.getClass().getMethod("preparePipeline", currentDimension.getClass());
			Object pipeline = preparePipeline.invoke(pipelineManager, currentDimension);
			if (pipeline == null) {
				LOGGER.warn("Iris returned a null pipeline; skipping Iris preRender.");
				return false;
			}
			PIPELINE.set(renderer, pipeline);
			Class<?> phaseClass = Class.forName(WORLD_RENDERING_PHASE_CLASS_NAME);
			Object nonePhase = phaseClass.getField("NONE").get(null);
			pipeline.getClass().getMethod("setOverridePhase", phaseClass).invoke(pipeline, nonePhase);
			return true;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Exception in Iris preRender", e);
			return false;
		}
	}

	public static void postRender(LevelRenderer renderer) {
		if (PIPELINE == null) {
			LOGGER.warn("Iris pipeline field missing; skipping Iris postRender.");
			return;
		}
		try {
			PIPELINE.set(renderer, null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Exception in Iris postRender", e);
		}
	}

	public static boolean shadersEnabled() {
		try {
			Class<?> irisApiClass = Class.forName(IRIS_API_CLASS_NAME);
			Object irisApi = irisApiClass.getMethod("getInstance").invoke(null);
			Object enabled = irisApiClass.getMethod("isShaderPackInUse").invoke(irisApi);
			return enabled instanceof Boolean && (Boolean) enabled;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LOGGER.warn("Failed to query Iris shader state", e);
			return false;
		}
	}
}
