package i5.las2peer.services.socialBotManagerService.dialogue.manager.task;

import java.util.ArrayList;
import java.util.Collection;

import i5.las2peer.services.socialBotManagerService.dialogue.DialogueAct;
import i5.las2peer.services.socialBotManagerService.dialogue.DialogueActGenerator;
import i5.las2peer.services.socialBotManagerService.dialogue.manager.AbstractDialogueManager;
import i5.las2peer.services.socialBotManagerService.dialogue.manager.task.goal.DialogueGoal;
import i5.las2peer.services.socialBotManagerService.dialogue.manager.task.goal.Fillable;
import i5.las2peer.services.socialBotManagerService.dialogue.manager.task.goal.Node;
import i5.las2peer.services.socialBotManagerService.dialogue.manager.task.goal.RepetitionNode;
import i5.las2peer.services.socialBotManagerService.dialogue.manager.task.goal.Slotable;
import i5.las2peer.services.socialBotManagerService.model.Slot;
import i5.las2peer.services.socialBotManagerService.nlu.Entity;
import i5.las2peer.services.socialBotManagerService.nlu.Intent;
import i5.las2peer.services.socialBotManagerService.nlu.IntentType;

public class DefaultDialogueManager extends AbstractDialogueManager {

    DialogueGoal goal;
    DialogueActGenerator gen;
    String start_intent;
    boolean optional;

    public DefaultDialogueManager(DialogueGoal goal) {
	this.goal = goal;
	this.gen = new DialogueActGenerator();
    }

    @Override
    public DialogueAct handle(Intent semantic) {

	assert semantic != null : "naive dm handle: semantic is null";
	assert semantic.getKeyword() != null : "naive dm handle: semantic has no intent";
	assert semantic.getIntentType() != null : "no intent type set";

	// first call
	String intent = semantic.getKeyword();
	if (intent.equals(start_intent))
	    return requestNextSlot();

	// get corresponding slot
	Slotable slo = null;
	if (goal.contains(intent)) {
	    slo = goal.getNode(intent);
	    if (slo == null)
		System.out.println("naive dm handle: slot not found for intent: " + intent);
	}

	// Repetition Node
	if (slo instanceof RepetitionNode) {
	    RepetitionNode rep = (RepetitionNode) slo;
	    if (semantic.getIntentType() == IntentType.CONFIRM) {
		rep.extend();
		return requestNextSlot();
	    }
	    if (semantic.getIntentType() == IntentType.DENY) {
		rep.close();
		if (goal.isFull())
		    return gen.getReqConfAct(goal.getRoot());
		return requestNextSlot();
	    }
	}

	// Value Nodes
	Fillable node = (Fillable) slo;
	DialogueAct act = new DialogueAct();
	switch (semantic.getIntentType()) {
	case INFORM:

	    // fill slot
	    for (Entity entity : semantic.getEntities()) {
		String value = entity.getValue();
		if (node.validate(value))
		    this.goal.fill(node, value);
		else
		    return requestNextSlot();
	    }

	    // arrays
	    if (node != null && node.getSlot().isArray()) {
		return gen.getReqConfArrayAct(node);

	    } else if (!optional && goal.isReady())
		return gen.getReqConfAct(goal.getRoot());

	    // check if full
	    if (goal.isFull())
		return gen.getReqConfAct(goal.getRoot());

	    return requestNextSlot();

	case REQUEST:

	    // inform about filled slot value
	    if (node != null)
		return gen.getInformAct(node);

	    // inform about expected slot value
	    // TODO

	case CONFIRM:

	    // user confirms that collected slot information is correct
	    if (semantic.getKeyword().contentEquals(goal.getFrame().getConfirmIntent())) {

		// ask if optional slots should be filled
		if (!goal.isFull() && goal.isReady()) {
		    act = gen.getReqOptionalAct(goal.getRoot());
		    this.optional = true;
		    return act;
		}

		// perform the action
		return perform();
	    }

	    // user want to fill further optional slots
	    if (semantic.getKeyword().contentEquals(goal.getFrame().getConfirmIntent().concat("_optional")))
		return requestNextSlot();

	    // user wants to fill more values for same slot
	    if (node != null && semantic.getKeyword().contentEquals(node.getConfirmIntent()))
		return gen.getRequestAct(node);

	    // user confirm but bot dont know why
	    return this.handleDefault();

	case DENY:

	    // deny that collected information is correct
	    if (semantic.getKeyword().contentEquals(goal.getFrame().getConfirmIntent())) {

		// delete collected information and start all over
		this.reset();
		return requestNextSlot();
	    }

	    // deny optional frame
	    if (semantic.getKeyword().contentEquals(goal.getFrame().getConfirmIntent().concat("_optional")))
		return perform();

	    // deny array slot
	    if (node != null && semantic.getKeyword().contentEquals(node.getConfirmIntent())) {

		// check if ready
		if (!optional && goal.isReady())
		    return gen.getReqConfAct(goal.getRoot());

		// request next slot
		return requestNextSlot();
	    }

	    // User wants so delete specific slot
	    if (node != null && node.isFilled()) {
		goal.delete(node);
		return gen.getInformAct(node);
	    }

	default:
	    // request next slot
	    return new DialogueAct("naive dm default with known intent");
	}
    }

    @Override
    public boolean hasIntent(String intent) {
	assert intent != null : "intent parameter is null";
	assert this.start_intent != null : "no start intent defined";

	if (intent.contentEquals(this.start_intent))
	    return true;
	if (intent.contentEquals(goal.getFrame().getConfirmIntent())
		|| intent.contentEquals(goal.getFrame().getConfirmIntent() + "_optional"))
	    return true;
	return goal.contains(intent);
    }

    @Override
    public DialogueAct handleDefault() {
	return requestNextSlot();
    }

    public void reset() {
	goal.reset();
	optional = false;
    }

    private DialogueAct requestNextSlot() {
	assert goal != null : "goal is null";
	assert !goal.isFull() : "goal is already full";

	Node nextNode = goal.next();
	// Request to fill value
	if (nextNode instanceof Fillable) {
	    Fillable fi = (Fillable) nextNode;
	    return gen.getRequestAct(fi);
	}
	// Ask for repetition
	if (nextNode instanceof RepetitionNode) {
	    RepetitionNode rep = (RepetitionNode) nextNode;
	    return gen.getReqConfArrayAct(rep);
	}

	assert false : "next slot is no Fillable nor Repetition node: " + nextNode.getClass();
	return null;
    }

    private DialogueAct perform() {
	DialogueAct act = new DialogueAct();
	act.setAction(goal.getOpenAPIAction());
	act.setMessage("perform action");
	this.reset();
	return act;

    }

    private DialogueAct repeat() {

	DialogueAct act = new DialogueAct();
//
	return act;

    }

    public void setStartIntent(String intent) {
	this.start_intent = intent;
    }

    @Override
    public Collection<String> getIntents() {
	Collection<String> intents = new ArrayList<String>();
	Collection<Slot> slots = goal.getFrame().getSlots().values();
	for (Slot slot : slots) {
	    intents.add(slot.getConfirmIntent());
	    intents.add(slot.getInformIntent());
	    intents.add(slot.getRequestIntent());
	    intents.add(slot.getDenyIntent());
	}

	intents.add(start_intent);
	intents.add(goal.getFrame().getConfirmIntent());
	intents.add(goal.getFrame().getConfirmIntent() + "_optional");
	return intents;
    }

}
