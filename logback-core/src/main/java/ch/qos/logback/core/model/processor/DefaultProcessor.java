package ch.qos.logback.core.model.processor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.joran.spi.InterpretationContext;
import ch.qos.logback.core.joran.util.beans.BeanDescriptionCache;
import ch.qos.logback.core.model.ComponentModel;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.FilterReply;

public class DefaultProcessor extends ContextAwareBase {

	final InterpretationContext interpretationContext;
	final HashMap<Class<? extends Model>, Class<? extends ModelHandlerBase>> modelClassToHandlerMap = new HashMap<>();

	ModelFiler phaseOneFilter = new AllowAllModelFilter();
	ModelFiler phaseTwoFilter = new DenyAllModelFilter();
	ModelFiler phaseThreeFilter = new DenyAllModelFilter();

	public DefaultProcessor(Context context, InterpretationContext interpretationContext) {
		this.setContext(context);
		this.interpretationContext = interpretationContext;
	}

	public void addHandler(Class<? extends Model> modelClass, Class<? extends ModelHandlerBase> handlerClass) {
		modelClassToHandlerMap.put(modelClass, handlerClass);
	}

	public void process(Model model) {

		if (model == null) {
			addError("Expecting non null model to process");
			return;
		}
		interpretationContext.pushObject(context);
		int LIMIT = 3;
		for (int i = 0; i < LIMIT; i++) {
			int count = traverse(model, getPhaseOneFilter());
			System.out.println("p1 count=" + count);
			if (count == 0)
				break;
		}
		for (int i = 0; i < LIMIT; i++) {
			int count = traverse(model, getPhaseTwoFilter());
			System.out.println("p2 count=" + count);
			if (count == 0)
				break;
		}
		for (int i = 0; i < LIMIT; i++) {

			int count = traverse(model, getPhaseThreeFilter());
			System.out.println("p3 count=" + count);
			if (count == 0) {
				break;
			}
		}
		addInfo("End of configuration.");
		interpretationContext.popObject();
	}

	public ModelFiler getPhaseOneFilter() {
		return phaseOneFilter;
	}

	public ModelFiler getPhaseTwoFilter() {
		return phaseTwoFilter;
	}

	public ModelFiler getPhaseThreeFilter() {
		return phaseThreeFilter;
	}

	public void setPhaseOneFilter(ModelFiler phaseOneFilter) {
		this.phaseOneFilter = phaseOneFilter;
	}

	public void setPhaseTwoFilter(ModelFiler phaseTwoFilter) {
		this.phaseTwoFilter = phaseTwoFilter;
	}

	public void setPhaseThreeFilter(ModelFiler phaseThreeFilter) {
		this.phaseThreeFilter = phaseThreeFilter;
	}

	int traverse(Model model, ModelFiler modelFiler) {


		FilterReply filterReply = modelFiler.decide(model);
		if (filterReply == FilterReply.DENY)
			return 0;

		Class<? extends ModelHandlerBase> handlerClass = modelClassToHandlerMap.get(model.getClass());

		if (handlerClass == null) {
			addError("Can't handle model of type " + model.getClass() + "  with tag: " + model.getTag() + " at line "
					+ model.getLineNumber());
			return 0;
		}

		int count = 0;
		
		ModelHandlerBase handler = instantiateHandler(handlerClass);

		try {
			if (!handler.isSupportedModelType(model)) {
				addWarn("Skipping processing for model " + model.idString());
				return count;
			}
			boolean handledHere = false;
			if (model.isFirstPass()) {
				handledHere = true;
				handler.handle(interpretationContext, model);
				model.markAsHandled();
				count++;
			}
			// recurse into submodels handled or not
			int len = model.getSubModels().size();
			for (int i = 0; i < len; i++) {
				Model m = model.getSubModels().get(i);
				count += traverse(m, modelFiler);
			}
			if (handledHere) {
				handler.postHandle(interpretationContext, model);
			}
		} catch (ModelHandlerException e) {
			addError("Failed to traverse model " + model.getTag(), e);
		}
		return count;
	}

	ModelHandlerBase instantiateHandler(Class<? extends ModelHandlerBase> handlerClass) {
		try {
			Constructor<? extends ModelHandlerBase> commonConstructor = getWithContextConstructor(handlerClass);
			if (commonConstructor != null) {
				return commonConstructor.newInstance(context);
			}
			Constructor<? extends ModelHandlerBase> constructorWithBDC = getWithContextAndBDCConstructor(handlerClass);
			if (constructorWithBDC != null) {
				return constructorWithBDC.newInstance(context, interpretationContext.getBeanDescriptionCache());
			}
			addError("Failed to find suitable constructor for class [" + handlerClass + "]");
			return null;
		} catch (InstantiationException | IllegalAccessException | SecurityException | IllegalArgumentException
				| InvocationTargetException e1) {
			addError("Failed to instantiate " + handlerClass);
			return null;
		}
	}

	private Constructor<? extends ModelHandlerBase> getWithContextConstructor(
			Class<? extends ModelHandlerBase> handlerClass) {
		try {
			Constructor<? extends ModelHandlerBase> constructor = handlerClass.getConstructor(Context.class);
			return constructor;
		} catch (NoSuchMethodException e) {
			return null;
		}
	}

	private Constructor<? extends ModelHandlerBase> getWithContextAndBDCConstructor(
			Class<? extends ModelHandlerBase> handlerClass) {
		try {
			Constructor<? extends ModelHandlerBase> constructor = handlerClass.getConstructor(Context.class,
					BeanDescriptionCache.class);
			return constructor;
		} catch (NoSuchMethodException e) {
			return null;
		}
	}
}
