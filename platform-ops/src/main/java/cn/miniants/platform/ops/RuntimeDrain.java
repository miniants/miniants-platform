package cn.miniants.platform.ops;

/**
 * 进程级排空开关。工人在取新任务前看 {@link #isDraining()}。
 */
public interface RuntimeDrain {

    boolean isDraining();

    void setDraining(boolean draining);
}
