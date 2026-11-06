package net.moriis.moriisexpansions.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.Supplier;

public class CannabisCropBlock extends CropBlock {

    public static final EnumProperty<DoubleBlockHalf> HALF =
            BlockStateProperties.DOUBLE_BLOCK_HALF;

    public static final int UPPER_START_AGE = 4;

    private final Supplier<? extends ItemLike> seedItem;

    public CannabisCropBlock(
            BlockBehaviour.Properties properties,
            Supplier<? extends ItemLike> seedItem
    ) {
        super(properties);
        this.seedItem = seedItem;

        registerDefaultState(
                stateDefinition.any()
                        .setValue(AGE, 0)
                        .setValue(HALF, DoubleBlockHalf.LOWER)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        super.createBlockStateDefinition(builder);
        builder.add(HALF);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return seedItem.get();
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        // Auch ausgewachsene Pflanzen müssen auf Licht geprüft werden.
        return state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public void randomTick(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random
    ) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        if (!level.isAreaLoaded(pos, 1)) {
            return;
        }

        // Berücksichtigt Blocklicht und das tageszeitabhängige Himmelslicht.
        int light = level.getMaxLocalRawBrightness(pos);

        if (light < 12) {
            // Entfernt über unsere onRemove-Methode auch die obere Hälfte.
            // true = normale Drops entsprechend der Wachstumsstufe.
            level.destroyBlock(pos, true);
            return;
        }

        if (isMaxAge(state)
                || !hasRoomForAge(level, pos, getAge(state) + 1)) {
            return;
        }

        super.randomTick(state, level, pos, random);

        BlockState current = level.getBlockState(pos);

        if (current.is(this)
                && current.getValue(HALF) == DoubleBlockHalf.LOWER) {
            synchronizeUpperHalf(level, pos, current);
        }
    }

    @Override
    public boolean canSurvive(
            BlockState state,
            LevelReader level,
            BlockPos pos
    ) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState lower = level.getBlockState(pos.below());

            return lower.is(this)
                    && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                    && getAge(lower) >= UPPER_START_AGE;
        }

        return super.canSurvive(state, level, pos);
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            if (direction == Direction.DOWN) {
                if (!neighborState.is(this)
                        || neighborState.getValue(HALF)
                        != DoubleBlockHalf.LOWER
                        || getAge(neighborState) < UPPER_START_AGE) {
                    return Blocks.AIR.defaultBlockState();
                }

                return state.setValue(AGE, getAge(neighborState));
            }

            return state;
        }

        return super.updateShape(
                state, direction, neighborState, level, pos, neighborPos
        );
    }

    @Override
    public boolean isValidBonemealTarget(
            LevelReader level,
            BlockPos pos,
            BlockState state
    ) {
        BlockPos lowerPos = getLowerPos(pos, state);
        BlockState lower = level.getBlockState(lowerPos);

        return lower.is(this)
                && lower.getValue(HALF) == DoubleBlockHalf.LOWER
                && !isMaxAge(lower)
                && hasRoomForAge(level, lowerPos, getAge(lower) + 1);
    }

    @Override
    public void growCrops(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        BlockPos lowerPos = getLowerPos(pos, state);
        BlockState lower = level.getBlockState(lowerPos);

        if (!lower.is(this)
                || lower.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        int age = getAge(lower);
        int nextAge = Math.min(
                getMaxAge(),
                age + getBonemealAgeIncrease(level)
        );

        // Knochenmehl darf ohne Platz oben nicht über age=3 springen.
        if (!hasRoomForAge(level, lowerPos, nextAge)) {
            nextAge = Math.min(nextAge, UPPER_START_AGE - 1);
        }

        if (nextAge <= age) {
            return;
        }

        BlockState grown = lower.setValue(AGE, nextAge);

        level.setBlock(lowerPos, grown, Block.UPDATE_CLIENTS);
        synchronizeUpperHalf(level, lowerPos, grown);
        level.updateNeighborsAt(lowerPos, this);
    }

    private boolean hasRoomForAge(
            LevelReader level,
            BlockPos lowerPos,
            int age
    ) {
        if (age < UPPER_START_AGE) {
            return true;
        }

        BlockPos upperPos = lowerPos.above();

        if (upperPos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }

        BlockState upper = level.getBlockState(upperPos);

        return upper.isAir()
                || (upper.is(this)
                && upper.getValue(HALF) == DoubleBlockHalf.UPPER);
    }

    private void synchronizeUpperHalf(
            Level level,
            BlockPos lowerPos,
            BlockState lower
    ) {
        int age = getAge(lower);

        if (age < UPPER_START_AGE
                || !hasRoomForAge(level, lowerPos, age)) {
            return;
        }

        BlockState upper = defaultBlockState()
                .setValue(HALF, DoubleBlockHalf.UPPER)
                .setValue(AGE, age);

        BlockPos upperPos = lowerPos.above();

        if (!level.getBlockState(upperPos).equals(upper)) {
            level.setBlock(upperPos, upper, Block.UPDATE_ALL);
        }
    }

    private BlockPos getLowerPos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER
                ? pos.below()
                : pos;
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (!level.isClientSide && !newState.is(this)) {
            boolean isLower =
                    state.getValue(HALF) == DoubleBlockHalf.LOWER;

            BlockPos otherPos = isLower ? pos.above() : pos.below();
            BlockState other = level.getBlockState(otherPos);

            if (other.is(this)
                    && other.getValue(HALF) != state.getValue(HALF)) {
                // Beim Entfernen oben erzeugt unten die Drops.
                // Beim Entfernen unten wird oben ohne Drops entfernt.
                level.destroyBlock(otherPos, !isLower);
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(
            Level level,
            BlockPos pos,
            BlockState state,
            Player player
    ) {
        if (!level.isClientSide
                && player.isCreative()
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pos.below();
            BlockState lower = level.getBlockState(lowerPos);

            if (lower.is(this)
                    && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.destroyBlock(lowerPos, false);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        int age = getAge(state);

        if (age < UPPER_START_AGE) {
            return super.getShape(state, level, pos, context)
                    .move(0.0, -1.0 / 16.0, 0.0);
        }

        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return Block.box(0, 0, 0, 16, 16, 16)
                    .move(0.0, -1.0 / 16.0, 0.0);
        }

        double height = Math.min(
                16.0,
                4.0 * (age - UPPER_START_AGE + 1)
        );

        return Block.box(0, 0, 0, 16, height, 16)
                .move(0.0, -1.0 / 16.0, 0.0);
    }
}