# 04 - Organising a user interface

*How do I keep presentation separate from the logic underneath it?* Four answers to the
same question, in rough historical order. They share one goal: **the rules must be
testable and reusable without a screen.**

For ShopStream that goal is concrete. The same purchase logic runs behind a web client, an
iOS app, an Android app and a seller dashboard. Any logic that lives in a view is logic
implemented four times and fixed three.

---

## MVC - Model View Controller

**What it is.** **Model** holds state and rules. **View** renders it. **Controller** takes
input and updates the model. Classically the view observes the model directly.

```text
   input
     |
     v
   Controller
     |  updates
     v
   Model
     |  notifies
     v
   View      renders the model
```

**The ShopStream case.** The server-rendered seller dashboard: a request hits a controller,
which invokes a use case, which updates the model, and a template renders the result.

**What it buys.** The oldest and most widely understood separation. Multiple views over one
model.

**What it costs.** "MVC" means noticeably different things in different frameworks, so the
term alone does not communicate a design. Controllers accumulate logic that belongs in the
domain - the god-controller is the standard decay, and it is the same defect as
`Bad_CheckoutEverything.java` in folder [08](../../08-soc-and-loose-coupling/).

---

## MVVM - Model View ViewModel

**What it is.** A **ViewModel** exposes the model in a form ready to display, and the view
**binds** to it declaratively. The view contains no logic; the ViewModel has no reference
to the view.

```text
   View  .....binding.....  ViewModel  .....reads.....  Model
   (no logic)                (display state,             (domain)
                              commands)

   the binding is two-way; the ViewModel never references the View
```

**The ShopStream case.** The mobile buyer app during a live stream. Viewer count, current
price, stock remaining and cart total all change continuously; binding means the view
updates without imperative refresh code. The ViewModel holds "is the buy button enabled",
which is display logic and genuinely testable without a device.

**What it buys.** Highly testable presentation logic. Very little view code. Excellent fit
for reactive and data-binding frameworks.

**What it costs.** ViewModels grow into a second domain layer if nobody watches. Binding
frameworks make data flow implicit, so debugging "why did this update?" gets harder.

---

## MVU - Model View Update

**What it is.** State is a single immutable value. The view is a **pure function** of that
state. Events produce a new state through an update function. Also called the Elm
architecture; the redux family follows it.

```text
   1.  Model     rendered by a pure view function into  UI
   2.  UI        emits an                                event
   3.  Update    takes the event plus the Model, returns a NEW Model
   4.  repeat
```

**The ShopStream case.** The live stream overlay, where several event sources - chat, price
changes, stock updates, viewer count - mutate shared UI state concurrently. A single
immutable state value with one update path removes the class of bugs where two handlers
race.

**What it buys.** Predictable, replayable state transitions. Time-travel debugging is
almost free. Concurrency bugs from shared mutable UI state largely disappear.

**What it costs.** Boilerplate: every interaction is an event type plus an update branch.
Rebuilding state on each change needs care to stay performant. Fits a functional style, and
fights an imperative one.

---

## PAC - Presentation Abstraction Control

**What it is.** The system is a **hierarchy of agents**, each with its own presentation,
abstraction (its data and rules) and control (which mediates and talks to other agents).
Agents communicate only through their control components.

```text
        [ P | A | C ]  top-level agent
              |
      +-------+-------+
  [P|A|C]         [P|A|C]     independent sub-agents
```

**The ShopStream case.** The seller's live studio screen: a stream-health panel, a chat
moderation panel, a live product list and a sales ticker. Each owns its data and refresh
cycle, and none should know the others exist. Coordination goes through the parent's
control component.

**What it buys.** Genuine independence between parts of a complex screen. Each agent is
developable and testable alone. It suits systems assembled from semi-autonomous panels.

**What it costs.** The most complex of the four, with a lot of mediation code. Rarely used
by name today - but the idea reappears as micro-frontends, widget architectures and
component hierarchies with isolated state, so the vocabulary is still worth having.
