package com.android.noisefield;

import static android.renderscript.Sampler.Value.NEAREST;
import static android.renderscript.Sampler.Value.WRAP;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.renderscript.Allocation;
import android.renderscript.Matrix4f;
import android.renderscript.Mesh;
import android.renderscript.Program;
import android.renderscript.ProgramFragment;
import android.renderscript.ProgramFragmentFixedFunction;
import android.renderscript.ProgramRaster;
import android.renderscript.ProgramStore;
import android.renderscript.ProgramVertex;
import android.renderscript.ProgramVertexFixedFunction;
import android.renderscript.RenderScriptGL;
import android.renderscript.Sampler;
import android.renderscript.ProgramStore.BlendDstFunc;
import android.renderscript.ProgramStore.BlendSrcFunc;
import android.util.Log;

public class NoiseFieldRS {

    public static final int DOT_COUNT = 300;

    private Resources mRes;
    private RenderScriptGL mRS;
    private ScriptC_noisefield mScript;
    int mHeight;
    int mWidth;
    private final BitmapFactory.Options mOptionsARGB = new BitmapFactory.Options();

    private ScriptField_VpConsts mPvConsts;
    private Allocation dotAllocation;
    private Allocation bgAllocation;
    private Allocation vignetteAllocation;

    private ScriptField_Particle dotParticles;
    private Mesh dotMesh;

    private int densityDPI;

    boolean inited = false;

    public void init(int dpi, RenderScriptGL rs, Resources res, int width, int height) {
        if (!inited) {
            densityDPI = dpi;

            mRS = rs;
            mRes = res;

            mWidth = width;
            mHeight = height;

            mOptionsARGB.inScaled = false;
            mOptionsARGB.inPreferredConfig = Bitmap.Config.ARGB_8888;

            dotParticles = new ScriptField_Particle(mRS, DOT_COUNT);
            Mesh.AllocationBuilder smb2 = new Mesh.AllocationBuilder(mRS);
            smb2.addVertexAllocation(dotParticles.getAllocation());
            smb2.addIndexSetType(Mesh.Primitive.POINT);
            dotMesh = smb2.create();

            mScript = new ScriptC_noisefield(mRS, mRes, R.raw.noisefield);
            mScript.set_dotMesh(dotMesh);
            mScript.bind_dotParticles(dotParticles);

            mPvConsts = new ScriptField_VpConsts(mRS, 1);

            createProgramVertex();
            createProgramRaster();
            createProgramFragmentStore();
            createProgramFragment();
            loadTextures();

            mScript.set_densityDPI(densityDPI);

            mRS.bindRootScript(mScript);

            mScript.invoke_positionParticles();
            inited = true;
        }
    }

    private Matrix4f getProjectionNormalized(int w, int h) {
        // range -1,1 in the narrow axis at z = 0.
        Matrix4f m1 = new Matrix4f();
        Matrix4f m2 = new Matrix4f();

        if (w > h) {
            float aspect = ((float) w) / h;
            m1.loadFrustum(-aspect, aspect, -1, 1, 1, 100);
        } else {
            float aspect = ((float) h) / w;
            m1.loadFrustum(-1, 1, -aspect, aspect, 1, 100);
        }

        m2.loadRotate(180, 0, 1, 0);
        m1.loadMultiply(m1, m2);

        m2.loadScale(-1, 1, 1);
        m1.loadMultiply(m1, m2);

        m2.loadTranslate(0, 0, 1);
        m1.loadMultiply(m1, m2);
        return m1;
    }

    private void updateProjectionMatrices() {
        Matrix4f proj = new Matrix4f();
        proj.loadOrthoWindow(mWidth, mHeight);

        Log.d("------------------- UPDATE PROJECTION MATRICES", mWidth + "  " + mHeight);

        Matrix4f projNorm = getProjectionNormalized(mWidth, mHeight);
        ScriptField_VpConsts.Item i = new ScriptField_VpConsts.Item();
        // i.Proj = projNorm;
        i.MVP = projNorm;
        mPvConsts.set(i, 0, true);
    }

    private Allocation loadTexture(int id) {
        final Allocation allocation = Allocation.createFromBitmapResource(mRS, mRes, id);
        return allocation;
    }

    private Allocation loadTextureARGB(int id) {
        Bitmap b = BitmapFactory.decodeResource(mRes, id, mOptionsARGB);
        return Allocation.createFromBitmap(mRS, b,
                Allocation.MipmapControl.MIPMAP_ON_SYNC_TO_TEXTURE,
                Allocation.USAGE_GRAPHICS_TEXTURE);
    }

    private void loadTextures() {
        dotAllocation = loadTexture(R.drawable.dot);
        bgAllocation = loadTexture(R.drawable.bg);
        vignetteAllocation = loadTextureARGB(R.drawable.vignette);
        mScript.set_textureDot(dotAllocation);
        mScript.set_textureBg(bgAllocation);
        mScript.set_textureVignette(vignetteAllocation);
    }

    private void createProgramVertex() {
        ProgramVertexFixedFunction.Constants mPvOrthoAlloc = new ProgramVertexFixedFunction.Constants(
                mRS);
        Matrix4f proj = new Matrix4f();
        proj.loadOrthoWindow(mWidth, mHeight);
        mPvOrthoAlloc.setProjection(proj);
        ProgramVertexFixedFunction.Builder pvb = new ProgramVertexFixedFunction.Builder(mRS);
        ProgramVertex pv = pvb.create();
        ((ProgramVertexFixedFunction) pv).bindConstants(mPvOrthoAlloc);
        mScript.set_vertBg(pv);

        updateProjectionMatrices();

        ProgramVertex.Builder builder = new ProgramVertex.Builder(mRS);
        builder = new ProgramVertex.Builder(mRS);
        builder.setShader(mRes, R.raw.noisefield_vs);
        builder.addConstant(mPvConsts.getType());
        builder.addInput(dotMesh.getVertexAllocation(0).getType().getElement());
        ProgramVertex pvs = builder.create();
        pvs.bindConstants(mPvConsts.getAllocation(), 0);
        mRS.bindProgramVertex(pvs);
        mScript.set_vertDots(pvs);

    }

    private void createProgramFragment() {
        Sampler.Builder samplerBuilder = new Sampler.Builder(mRS);
        samplerBuilder.setMinification(NEAREST);
        samplerBuilder.setMagnification(NEAREST);
        samplerBuilder.setWrapS(WRAP);
        samplerBuilder.setWrapT(WRAP);
        Sampler sn = samplerBuilder.create();
        ProgramFragmentFixedFunction.Builder builderff = new ProgramFragmentFixedFunction.Builder(
                mRS);
        builderff.setTexture(ProgramFragmentFixedFunction.Builder.EnvMode.REPLACE,
                ProgramFragmentFixedFunction.Builder.Format.RGB, 0);

        builderff.setVaryingColor(true);
        ProgramFragment pfff = builderff.create();

        mScript.set_fragBg(pfff);
        pfff.bindSampler(sn, 0);

        ProgramFragment.Builder builder = new ProgramFragment.Builder(mRS);
        builder = new ProgramFragment.Builder(mRS);
        builder.addTexture(Program.TextureType.TEXTURE_2D);
        builder.setShader(mRes, R.raw.noisefield_fs);
        ProgramFragment pf = builder.create();
        pf.bindSampler(Sampler.CLAMP_LINEAR(mRS), 0);
        mScript.set_fragDots(pf);
    }

    private void createProgramRaster() {
        ProgramRaster.Builder builder = new ProgramRaster.Builder(mRS);
        builder.setPointSpriteEnabled(true);
        ProgramRaster pr = builder.create();
        mRS.bindProgramRaster(pr);
    }

    private void createProgramFragmentStore() {
        ProgramStore.Builder builder = new ProgramStore.Builder(mRS);
        // builder.setBlendFunc(BlendSrcFunc.SRC_ALPHA, BlendDstFunc.ONE_MINUS_SRC_ALPHA );
        builder.setBlendFunc(BlendSrcFunc.SRC_ALPHA, BlendDstFunc.ONE);
        // why alpha no work with additive blending?
        // builder.setBlendFunc(BlendSrcFunc.ONE, BlendDstFunc.ONE);
        mRS.bindProgramStore(builder.create());
        mScript.set_storeAdd(builder.create());

        builder.setBlendFunc(BlendSrcFunc.SRC_ALPHA, BlendDstFunc.ONE_MINUS_SRC_ALPHA);
        mScript.set_storeAlpha(builder.create());
    }

    public void start() {
        mRS.bindRootScript(mScript);
    }

    public void stop() {
        mRS.bindRootScript(null);

    }

    public void setOffset(float xOffset, float yOffset, int xPixels, int yPixels) {
        mScript.set_xOffset(xOffset - 0.5f);
    }

    public void resize(int w, int h) {
        // why do i need to do this again when surface changed for wallpaper, but not when as an app?
        ProgramVertexFixedFunction.Constants mPvOrthoAlloc = new ProgramVertexFixedFunction.Constants(
                mRS);
        Matrix4f proj = new Matrix4f();
        proj.loadOrthoWindow(w, h);
        mPvOrthoAlloc.setProjection(proj);

        ProgramVertexFixedFunction.Builder pvb = new ProgramVertexFixedFunction.Builder(mRS);
        ProgramVertex pv = pvb.create();
        ((ProgramVertexFixedFunction) pv).bindConstants(mPvOrthoAlloc);
        mScript.set_vertBg(pv);
    }

}
