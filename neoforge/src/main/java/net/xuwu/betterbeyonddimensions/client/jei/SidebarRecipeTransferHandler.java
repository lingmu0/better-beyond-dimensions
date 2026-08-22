package net.xuwu.betterbeyonddimensions.client.jei;

import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.xuwu.betterbeyonddimensions.NetworkHandler;
import net.xuwu.betterbeyonddimensions.client.ClientStorageState;
import net.xuwu.betterbeyonddimensions.common.RecipeFill;
import net.xuwu.betterbeyonddimensions.common.StorageEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JEI transfer handler that treats the Beyond Dimensions network as an input source. */
public final class SidebarRecipeTransferHandler<C extends AbstractContainerMenu, R>
        implements IRecipeTransferHandler<C, R>
{
    private static final int MAX_TRANSFER_MULTIPLIER = 64;

    private final Class<? extends C> containerClass;
    private final RecipeType<R> recipeType;
    private final IRecipeTransferHandlerHelper helper;

    public SidebarRecipeTransferHandler(Class<? extends C> containerClass, RecipeType<R> recipeType,
                                        IRecipeTransferHandlerHelper helper)
    {
        this.containerClass = containerClass;
        this.recipeType = recipeType;
        this.helper = helper;
    }

    @Override
    public Class<? extends C> getContainerClass()
    {
        return containerClass;
    }

    @Override
    public Optional<net.minecraft.world.inventory.MenuType<C>> getMenuType()
    {
        return Optional.empty();
    }

    @Override
    public RecipeType<R> getRecipeType()
    {
        return recipeType;
    }

    @Override
    public IRecipeTransferError transferRecipe(C menu, R recipe, IRecipeSlotsView recipeSlots,
                                                Player player, boolean maxTransfer, boolean doTransfer)
    {
        if (helper == null || menu == null || player == null)
        {
            return helper == null ? null : helper.createInternalError();
        }

        List<Slot> targetSlots = getCraftingSlots(menu);
        List<IRecipeSlotView> inputViews = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
        if (targetSlots.isEmpty() || inputViews.size() > targetSlots.size())
        {
            return helper.createInternalError();
        }

        SourcePool pool = SourcePool.from(menu, player);
        List<PlannedFill> planned = new ArrayList<>();
        List<IRecipeSlotView> missing = new ArrayList<>();
        Map<ItemStackKey, Long> requirements = new LinkedHashMap<>();

        for (int index = 0; index < targetSlots.size(); index++)
        {
            Slot target = targetSlots.get(index);
            IRecipeSlotView view = index < inputViews.size() ? inputViews.get(index) : null;
            List<ItemStack> variants = view == null
                    ? List.of()
                    : view.getItemStacks().filter(stack -> stack != null && !stack.isEmpty())
                    .map(ItemStack::copy).toList();
            if (variants.isEmpty())
            {
                planned.add(new PlannedFill(target.index, ItemStack.EMPTY, 0));
                continue;
            }

            ItemStack selected = variants.stream()
                    .max(Comparator.comparingLong(pool::availableFor))
                    .orElse(ItemStack.EMPTY);
            int required = variants.stream().mapToInt(ItemStack::getCount).max().orElse(1);
            required = Math.max(1, required);
            long available = pool.availableFor(selected);
            if (available < required)
            {
                if (view != null)
                {
                    missing.add(view);
                }
                continue;
            }

            pool.consume(selected, required);
            ItemStack requested = selected.copy();
            requested.setCount(1);
            planned.add(new PlannedFill(target.index, requested, required));
            ItemStackKey key = new ItemStackKey(requested);
            requirements.merge(key, (long) required, Long::sum);
        }

        long multiplier = maxTransfer ? MAX_TRANSFER_MULTIPLIER : 1L;
        for (PlannedFill fill : planned)
        {
            if (fill.stack().isEmpty())
            {
                continue;
            }
            int slotLimit = Math.max(1, Math.min(fill.stack().getMaxStackSize(),
                    targetSlots.stream().filter(slot -> slot.index == fill.slotId())
                            .findFirst().map(slot -> slot.getMaxStackSize(fill.stack())).orElse(64)));
            multiplier = Math.min(multiplier, Math.max(1L, slotLimit / (long) fill.amount()));
        }

        if (!requirements.isEmpty())
        {
            for (Map.Entry<ItemStackKey, Long> requirement : requirements.entrySet())
            {
                ItemStack stack = requirement.getKey().copyStack();
                long available = SourcePool.from(menu, player).availableFor(stack);
                long possible = available / Math.max(1L, requirement.getValue());
                multiplier = Math.min(multiplier, possible);
            }
        }

        if (!missing.isEmpty() || multiplier < 1L)
        {
            return helper.createUserErrorForMissingSlots(
                    Component.translatable("better_beyond_dimensions.jei.missing_ingredients"), missing);
        }

        if (doTransfer)
        {
            List<RecipeFill> fills = new ArrayList<>(planned.size());
            for (PlannedFill fill : planned)
            {
                int amount = fill.stack().isEmpty() ? 0
                        : (int) Math.min(64L, Math.max(1L, fill.amount() * multiplier));
                fills.add(new RecipeFill(fill.slotId(), fill.stack(), amount));
            }
            NetworkHandler.fillRecipe(fills);
        }
        return null;
    }

    private static List<Slot> getCraftingSlots(AbstractContainerMenu menu)
    {
        List<Slot> result = new ArrayList<>();
        for (Slot slot : menu.slots)
        {
            if (slot.container instanceof CraftingContainer)
            {
                result.add(slot);
            }
        }
        return result;
    }

    private record PlannedFill(int slotId, ItemStack stack, int amount)
    {
    }

    private static final class SourcePool
    {
        private final List<AvailableStack> values = new ArrayList<>();

        static SourcePool from(AbstractContainerMenu menu, Player player)
        {
            SourcePool pool = new SourcePool();
            for (StorageEntry entry : ClientStorageState.snapshot().entries())
            {
                if (entry != null && entry.amount() > 0L)
                {
                    pool.add(entry.stack(), entry.amount());
                }
            }
            for (ItemStack stack : player.getInventory().items)
            {
                pool.add(stack, stack == null ? 0L : stack.getCount());
            }
            for (Slot slot : getCraftingSlots(menu))
            {
                pool.add(slot.getItem(), slot.getItem().isEmpty() ? 0L : slot.getItem().getCount());
            }
            return pool;
        }

        long availableFor(ItemStack wanted)
        {
            long total = 0L;
            for (AvailableStack value : values)
            {
                if (sameStoredStack(value.stack, wanted))
                {
                    total += value.amount;
                }
            }
            return total;
        }

        void consume(ItemStack wanted, long amount)
        {
            long remaining = amount;
            for (AvailableStack value : values)
            {
                if (remaining <= 0L)
                {
                    return;
                }
                if (!sameStoredStack(value.stack, wanted))
                {
                    continue;
                }
                long moved = Math.min(value.amount, remaining);
                value.amount -= moved;
                remaining -= moved;
            }
        }

        private void add(ItemStack stack, long amount)
        {
            if (stack == null || stack.isEmpty() || amount <= 0L)
            {
                return;
            }
            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            for (AvailableStack value : values)
            {
                if (sameStoredStack(value.stack, normalized))
                {
                    value.amount += amount;
                    return;
                }
            }
            values.add(new AvailableStack(normalized, amount));
        }
    }

    private static final class AvailableStack
    {
        private final ItemStack stack;
        private long amount;

        private AvailableStack(ItemStack stack, long amount)
        {
            this.stack = stack;
            this.amount = amount;
        }
    }

    private static boolean sameStoredStack(ItemStack first, ItemStack second)
    {
        return first != null && second != null && !first.isEmpty() && !second.isEmpty()
                && new ItemStackKey(first).equals(new ItemStackKey(second));
    }
}
