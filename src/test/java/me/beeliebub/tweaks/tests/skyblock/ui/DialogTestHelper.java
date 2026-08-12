package me.beeliebub.tweaks.tests.skyblock.ui;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.ConfirmationType;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.type.MultiActionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.mockbukkit.mockbukkit.dialog.DialogMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.simulate.dialog.DialogInputValues;
import org.mockbukkit.mockbukkit.simulate.dialog.DialogSimulation;
import org.mockbukkit.mockbukkit.ServerMock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Small compatibility seam around the local MockBukkit Dialog implementation. */
public final class DialogTestHelper {
    private DialogTestHelper() {
    }

    public static Object openDialog(PlayerMock player) {
        for (String name : new String[]{"getDialog", "getOpenDialog", "getCurrentDialog"}) {
            Object value = invokeNoArg(player, name);
            if (value != null) return unwrap(value);
        }
        for (Field field : allFields(player.getClass())) {
            if (!field.getType().getSimpleName().contains("Dialog")) continue;
            try {
                field.setAccessible(true);
                Object value = unwrap(field.get(player));
                if (value != null) return value;
            } catch (ReflectiveOperationException ignored) {
                // Try the next MockBukkit representation.
            }
        }
        return null;
    }

    public static Object requireOpenDialog(PlayerMock player) {
        Object dialog = openDialog(player);
        assertNotNull(dialog, "the player should have an open Dialog");
        return dialog;
    }

    public static DialogMock requireDialog(PlayerMock player) {
        Object dialog = requireOpenDialog(player);
        if (!(dialog instanceof DialogMock mock)) {
            throw new AssertionError("MockBukkit should expose its DialogMock instance");
        }
        return mock;
    }

    public static List<ActionButton> buttons(Object dialog) {
        if (!(dialog instanceof DialogMock mock)) {
            throw new AssertionError("Expected a MockBukkit DialogMock");
        }
        DialogType type = mock.getEntry().type();
        if (type instanceof ConfirmationType confirmation) {
            return List.of(confirmation.yesButton(), confirmation.noButton());
        }
        if (type instanceof MultiActionType multiAction) {
            List<ActionButton> buttons = new ArrayList<>(multiAction.actions());
            if (multiAction.exitAction() != null) buttons.add(multiAction.exitAction());
            return List.copyOf(buttons);
        }
        return List.of();
    }

    public static List<String> inputKeys(Object dialog) {
        if (!(dialog instanceof DialogMock mock)) {
            throw new AssertionError("Expected a MockBukkit DialogMock");
        }
        DialogBase base = mock.getEntry().base();
        return base.inputs().stream().map(DialogInput::key).toList();
    }

    public static String label(ActionButton button) {
        Component label = button == null ? null : button.label();
        return label == null ? "" : PlainTextComponentSerializer.plainText().serialize(label);
    }

    public static void click(PlayerMock player, ActionButton button, Map<String, String> values) {
        DialogInputValues inputValues = new DialogInputValues();
        if (values != null) values.forEach(inputValues::text);
        new DialogSimulation(player).simulateClick(button, inputValues);
    }

    public static void click(PlayerMock player, ActionButton button) {
        new DialogSimulation(player).simulateClick(button);
    }

    public static void pump(ServerMock server) {
        server.getScheduler().performOneTick();
    }

    public static Object invoke(Object target, String methodName, Object... arguments) {
        Class<?> type = target.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) continue;
                try {
                    method.setAccessible(true);
                    return method.invoke(target, arguments);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError("Could not invoke " + methodName, error);
                }
            }
            type = type.getSuperclass();
        }
        throw new AssertionError("No method named " + methodName + " on " + target.getClass().getName());
    }

    public static Object field(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException error) {
                throw new AssertionError("Could not read " + fieldName, error);
            }
        }
        throw new AssertionError("No field named " + fieldName + " on " + target.getClass().getName());
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return unwrap(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object unwrap(Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        return value;
    }

    private static Field[] allFields(Class<?> type) {
        java.util.List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            fields.addAll(java.util.Arrays.asList(current.getDeclaredFields()));
        }
        return fields.toArray(Field[]::new);
    }
}
