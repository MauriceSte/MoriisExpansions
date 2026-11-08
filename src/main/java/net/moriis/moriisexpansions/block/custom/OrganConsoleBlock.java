package net.moriis.moriisexpansions.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import net.moriis.moriisexpansions.client.screen.OrganScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class OrganConsoleBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    public OrganConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(LEFT, false).setValue(RIGHT, false));
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, LEFT, RIGHT);
    }
    private static boolean matches(BlockState state, Block block, Direction facing, DoubleBlockHalf half) {
        return state.is(block) && state.hasProperty(HALF)
                && state.getValue(FACING) == facing && state.getValue(HALF) == half;
    }
    private static Direction left(Direction facing) { return facing.getClockWise(); }
    private static int count(BlockGetter level, BlockPos pos, Block block, Direction facing, Direction step) {
        int n = 0;
        for (int i = 1; i <= 4; i++) {
            if (!matches(level.getBlockState(pos.relative(step, i)), block, facing, DoubleBlockHalf.LOWER)) break;
            n++;
        }
        return n;
    }
    private static BlockState connected(BlockGetter level, BlockPos pos, BlockState state) {
        Direction f = state.getValue(FACING);
        return state.setValue(LEFT, matches(level.getBlockState(pos.relative(left(f))),
                        state.getBlock(), f, state.getValue(HALF)))
                .setValue(RIGHT, matches(level.getBlockState(pos.relative(left(f).getOpposite())),
                        state.getBlock(), f, state.getValue(HALF)));
    }
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction f = context.getHorizontalDirection().getOpposite();
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        int length = 1 + count(level, pos, this, f, left(f))
                + count(level, pos, this, f, left(f).getOpposite());
        if (length > 4) {
            if (context.getPlayer() != null && level.isClientSide)
                context.getPlayer().displayClientMessage(Component.translatable("message.moriisexpansions.organ.too_long"), true);
            return null;
        }
        return connected(level, pos, defaultBlockState().setValue(FACING, f));
    }
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity entity, ItemStack stack) {
        BlockState upper = state.setValue(HALF, DoubleBlockHalf.UPPER);
        level.setBlock(pos.above(), connected(level, pos.above(), upper), Block.UPDATE_ALL);
    }
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        Direction partner = state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        if (direction == partner && !matches(neighbor, this, state.getValue(FACING),
                state.getValue(HALF) == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER))
            return Blocks.AIR.defaultBlockState();
        return connected(level, pos, state);
    }
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide) {
            boolean upper = state.getValue(HALF) == DoubleBlockHalf.UPPER;
            BlockPos other = upper ? pos.below() : pos.above();
            if (matches(level.getBlockState(other), this, state.getValue(FACING),
                    upper ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER)) {
                level.destroyBlock(other, upper);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lower = pos.below();
            if (matches(level.getBlockState(lower), this, state.getValue(FACING), DoubleBlockHalf.LOWER))
                level.setBlock(lower, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
    /** Left end of this straight row; null for incomplete or oversized structures. */
    public static BlockPos playableAnchor(BlockGetter level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof OrganConsoleBlock)) return null;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.UPPER ? clicked.below() : clicked;
        Direction f = state.getValue(FACING);
        Block block = state.getBlock();
        if (!matches(level.getBlockState(lower), block, f, DoubleBlockHalf.LOWER)) return null;
        int l = count(level, lower, block, f, left(f));
        int r = count(level, lower, block, f, left(f).getOpposite());
        int length = 1 + l + r;
        if (length < 3 || length > 4) return null;
        BlockPos anchor = lower.relative(left(f), l);
        for (int i = 0; i < length; i++) {
            BlockPos part = anchor.relative(left(f).getOpposite(), i);
            if (!matches(level.getBlockState(part.above()), block, f, DoubleBlockHalf.UPPER)) return null;
        }
        return anchor;
    }
    public static boolean isPlayableAnchor(BlockGetter level, BlockPos pos) {
        return pos.equals(playableAnchor(level, pos));
    }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        BlockPos anchor = playableAnchor(level, pos);
        if (anchor == null) {
            if (!level.isClientSide) player.displayClientMessage(
                    Component.translatable("message.moriisexpansions.organ.incomplete"), true);
        } else if (level.isClientSide) ClientAccess.open(anchor);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) return Shapes.block();
        // Upper model has a 4-unit platform and an 8-unit rear upright.
        return switch (state.getValue(FACING)) {
            case SOUTH -> Shapes.or(Block.box(0, 0, 0, 16, 4, 9), Block.box(0, 4, 0, 16, 8, 2));
            case NORTH -> Shapes.or(Block.box(0, 0, 7, 16, 4, 16), Block.box(0, 4, 14, 16, 8, 16));
            case WEST -> Shapes.or(Block.box(7, 0, 0, 16, 4, 16), Block.box(14, 4, 0, 16, 8, 16));
            default -> Shapes.or(Block.box(0, 0, 0, 9, 4, 16), Block.box(0, 4, 0, 2, 8, 16));
        };
    }
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        boolean l = state.getValue(LEFT), r = state.getValue(RIGHT);
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(LEFT, r).setValue(RIGHT, l);
    }
    @OnlyIn(Dist.CLIENT)
    private static final class ClientAccess {
        private static void open(BlockPos pos) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                    new OrganScreen(pos));
        }
    }
}
