package com.android.noisefield;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.renderscript.Allocation;
import android.renderscript.Float3;
import android.renderscript.Float4;
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
import android.renderscript.Mesh.Primitive;
import android.renderscript.ProgramStore.BlendDstFunc;
import android.renderscript.ProgramStore.BlendSrcFunc;
import android.util.Log;

public class NoiseFieldRS {

    private Resources mRes;
    private RenderScriptGL mRS;
    private ScriptC_noisefield mScript;
    int mHeight;
    int mWidth;
    private final BitmapFactory.Options mOptionsARGB = new BitmapFactory.Options();

    private ScriptField_VpConsts mPvConsts;
    private Allocation mDotAllocation;
    private ScriptField_VertexColor_s mVertexColors;
    private ScriptField_Particle mDotParticles;
    private Mesh mDotMesh;
    private int mDensityDPI;

    public void init(int dpi, RenderScriptGL rs,
                     Resources res, int width, int height) {
        mDensityDPI = dpi;

        mRS = rs;
        mRes = res;

        mWidth = width;
        mHeight = height;

        mOptionsARGB.inScaled = false;
        mOptionsARGB.inPreferredConfig = Bitmap.Config.ARGB_8888;

        Mesh.AllocationBuilder smb2 = new Mesh.AllocationBuilder(mRS);

        mDotParticles = new ScriptField_Particle(mRS, 150);
        smb2.addVertexAllocation(mDotParticles.getAllocation());

        smb2.addIndexSetType(Mesh.Primitive.POINT);
        mScript = new ScriptC_noisefield(mRS, mRes, R.raw.noisefield);


        mDotMesh = smb2.create();
        mScript.set_dotMesh(mDotMesh);
        mScript.bind_dotParticles(mDotParticles);

        mPvConsts = new ScriptField_VpConsts(mRS, 1);

        createProgramVertex();
        createProgramRaster();
        createProgramFragmentStore();
        createProgramFragment();
        createBackgroundMesh();
        loadTextures();

        mScript.set_densityDPI(mDensityDPI);
        mScript.invoke_positionParticles();
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
        Matrix4f projNorm = getProjectionNormalized(mWidth, mHeight);
        ScriptField_VpConsts.Item i = new ScriptField_VpConsts.Item();
        i.MVP = projNorm;
        i.scaleSize = mDensityDPI/240.0f;
        mPvConsts.set(i, 0, true);
    }

    private void createBackgroundMesh() {
        // The composition and colors of the background mesh were plotted on paper and photoshop
        // first then translated to the code you see below. Points and colors are not random.

        mVertexColors = new ScriptField_VertexColor_s(mRS, 48);
        Float3 a = new Float3(-1.5f, 1.0f, 0.0f);
        Float3 b = new Float3(0.0f, 1.0f, 0.0f);
        Float3 c = new Float3(1.5f, 1.0f, 0.0f);
        Float3 d = new Float3(-1.05f, 0.3f, 0.0f);
        Float3 e = new Float3(-0.6f, 0.4f, 0.0f);
        Float3 f = new Float3(0.3f, 0.4f, 0.0f);
        Float3 g = new Float3(0.0f, 0.2f, 0.0f);
        Float3 h = new Float3(-0.6f, 0.1f, 0.0f);
        Float3 i = new Float3(-1.5f, -0.2f, 0.0f);
        Float3 j = new Float3(-0.45f, -0.3f, 0.0f);
        Float3 k = new Float3(-1.5f, -1.0f, 0.0f);
        Float3 l = new Float3(1.5f, -1.0f, 0.0f);
        mVertexColors.set_position(0, a, false);
        mVertexColors.set_color(0, (new Float4(0.08f,0.335f,0.406f, 1.0f)), false);
        mVertexColors.set_position(1, i, false);
        mVertexColors.set_color(1, (new Float4(0.137f,0.176f,0.225f, 1.0f)), false);
        mVertexColors.set_position(2, d, false);
        mVertexColors.set_color(2, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(3, a, false);
        mVertexColors.set_color(3, (new Float4(0.08f,0.335f,0.406f, 1.0f)), false);
        mVertexColors.set_position(4, d, false);
        mVertexColors.set_color(4, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(5, e, false);
        mVertexColors.set_color(5, (new Float4(0.0f,0.184f,0.233f, 1.0f)), false);
        mVertexColors.set_position(6, a, false);
        mVertexColors.set_color(6, (new Float4(0.08f,0.335f,0.406f, 1.0f)), false);
        mVertexColors.set_position(7, e, false);
        mVertexColors.set_color(7, (new Float4(0.0f,0.184f,0.233f, 1.0f)), false);
        mVertexColors.set_position(8, b, false);
        mVertexColors.set_color(8, (new Float4(0.133f,0.404f,0.478f, 1.0f)), false);
        mVertexColors.set_position(9, b, false);
        mVertexColors.set_color(9, (new Float4(0.133f,0.404f,0.478f, 1.0f)), false);
        mVertexColors.set_position(10, e, false);
        mVertexColors.set_color(10, (new Float4(0.0f,0.184f,0.233f, 1.0f)), false);
        mVertexColors.set_position(11, f, false);
        mVertexColors.set_color(11, (new Float4(0.0f,0.124f,0.178f, 1.0f)), false);
        mVertexColors.set_position(12, b, false);
        mVertexColors.set_color(12, (new Float4(0.133f,0.404f,0.478f, 1.0f)), false);
        mVertexColors.set_position(13, f, false);
        mVertexColors.set_color(13, (new Float4(0.0f,0.124f,0.178f, 1.0f)), false);
        mVertexColors.set_position(14, c, false);
        mVertexColors.set_color(14, (new Float4(0.002f,0.173f,0.231f, 1.0f)), false);
        mVertexColors.set_position(15, c, false);
        mVertexColors.set_color(15, (new Float4(0.002f,0.173f,0.231f, 1.0f)), false);
        mVertexColors.set_position(16, f, false);
        mVertexColors.set_color(16, (new Float4(0.0f,0.124f,0.178f, 1.0f)), false);
        mVertexColors.set_position(17, l, false);
        mVertexColors.set_color(17, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(18, f, false);
        mVertexColors.set_color(18, (new Float4(0.0f,0.124f,0.178f, 1.0f)), false);
        mVertexColors.set_position(19, e, false);
        mVertexColors.set_color(19, (new Float4(0.0f,0.184f,0.233f, 1.0f)), false);
        mVertexColors.set_position(20, g, false);
        mVertexColors.set_color(20, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(21, f, false);
        mVertexColors.set_color(21, (new Float4(0.0f,0.124f,0.178f, 1.0f)), false);
        mVertexColors.set_position(22, g, false);
        mVertexColors.set_color(22, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(23, l, false);
        mVertexColors.set_color(23, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(24, g, false);
        mVertexColors.set_color(24, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(25, e, false);
        mVertexColors.set_color(25, (new Float4(0.0f,0.184f,0.233f, 1.0f)), false);
        mVertexColors.set_position(26, h, false);
        mVertexColors.set_color(26, (new Float4(0.002f,0.196f,0.233f, 1.0f)), false);
        mVertexColors.set_position(27, h, false);
        mVertexColors.set_color(27, (new Float4(0.002f,0.196f,0.233f, 1.0f)), false);
        mVertexColors.set_position(28, e, false);
        mVertexColors.set_color(28, (new Float4(0.0f,0.184f,0.233f, 1.0f)), false);
        mVertexColors.set_position(29, d, false);
        mVertexColors.set_color(29, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(30, d, false);
        mVertexColors.set_color(30, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(31, i, false);
        mVertexColors.set_color(31, (new Float4(0.137f,0.176f,0.225f, 1.0f)), false);
        mVertexColors.set_position(32, h, false);
        mVertexColors.set_color(32, (new Float4(0.002f,0.196f,0.233f, 1.0f)), false);
        mVertexColors.set_position(33, j, false);
        mVertexColors.set_color(33, (new Float4(0.002f,0.059f,0.09f, 1.0f)), false);
        mVertexColors.set_position(34, h, false);
        mVertexColors.set_color(34, (new Float4(0.002f,0.196f,0.233f, 1.0f)), false);
        mVertexColors.set_position(35, i, false);
        mVertexColors.set_color(35, (new Float4(0.137f,0.176f,0.225f, 1.0f)), false);
        mVertexColors.set_position(36, j, false);
        mVertexColors.set_color(36, (new Float4(0.002f,0.059f,0.09f, 1.0f)), false);
        mVertexColors.set_position(37, i, false);
        mVertexColors.set_color(37, (new Float4(0.137f,0.176f,0.225f, 1.0f)), false);
        mVertexColors.set_position(38, k, false);
        mVertexColors.set_color(38, (new Float4(0.204f,0.212f,0.218f, 1.0f)), false);
        mVertexColors.set_position(39, l, false);
        mVertexColors.set_color(39, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(40, j, false);
        mVertexColors.set_color(40, (new Float4(0.002f,0.059f,0.09f, 1.0f)), false);
        mVertexColors.set_position(41, k, false);
        mVertexColors.set_color(41, (new Float4(0.204f,0.212f,0.218f, 1.0f)), false);
        mVertexColors.set_position(42, g, false);
        mVertexColors.set_color(42, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(43, h, false);
        mVertexColors.set_color(43, (new Float4(0.002f,0.196f,0.233f, 1.0f)), false);
        mVertexColors.set_position(44, j, false);
        mVertexColors.set_color(44, (new Float4(0.002f,0.059f,0.09f, 1.0f)), false);
        mVertexColors.set_position(45, l, false);
        mVertexColors.set_color(45, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(46, g, false);
        mVertexColors.set_color(46, (new Float4(0.0f,0.088f,0.135f, 1.0f)), false);
        mVertexColors.set_position(47, j, false);
        mVertexColors.set_color(47, (new Float4(0.002f,0.059f,0.09f, 1.0f)), false);

        mVertexColors.copyAll();

        Mesh.AllocationBuilder backgroundBuilder = new Mesh.AllocationBuilder(mRS);
        backgroundBuilder.addIndexSetType(Primitive.TRIANGLE);
        backgroundBuilder.addVertexAllocation(mVertexColors.getAllocation());
        mScript.set_gBackgroundMesh(backgroundBuilder.create());

        mScript.bind_vertexColors(mVertexColors);
    }

    private Allocation loadTexture(int id) {
        final Allocation allocation = Allocation.createFromBitmapResource(mRS, mRes, id);
        return allocation;
    }

    private void loadTextures() {
        mDotAllocation = loadTexture(R.drawable.dot);
        mScript.set_textureDot(mDotAllocation);
    }

    private void createProgramVertex() {
        ProgramVertex.Builder backgroundBuilder = new ProgramVertex.Builder(mRS);
        backgroundBuilder.setShader(mRes, R.raw.bg_vs);
        backgroundBuilder.addInput(ScriptField_VertexColor_s.createElement(mRS));
        ProgramVertex programVertexBackground = backgroundBuilder.create();
        mScript.set_vertBg(programVertexBackground);


        updateProjectionMatrices();

        ProgramVertex.Builder builder = new ProgramVertex.Builder(mRS);
        builder = new ProgramVertex.Builder(mRS);
        builder.setShader(mRes, R.raw.noisefield_vs);
        builder.addConstant(mPvConsts.getType());
        builder.addInput(mDotMesh.getVertexAllocation(0).getType().getElement());

        ProgramVertex pvs = builder.create();
        pvs.bindConstants(mPvConsts.getAllocation(), 0);
        mRS.bindProgramVertex(pvs);
        mScript.set_vertDots(pvs);
    }

    private void createProgramFragment() {
        ProgramFragment.Builder backgroundBuilder = new ProgramFragment.Builder(mRS);
        backgroundBuilder.setShader(mRes, R.raw.bg_fs);
        ProgramFragment programFragmentBackground = backgroundBuilder.create();
        mScript.set_fragBg(programFragmentBackground);

        ProgramFragment.Builder builder = new ProgramFragment.Builder(mRS);
        builder.setShader(mRes, R.raw.noisefield_fs);
        builder.addTexture(Program.TextureType.TEXTURE_2D);
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
        builder.setBlendFunc(BlendSrcFunc.SRC_ALPHA, BlendDstFunc.ONE );
        mRS.bindProgramStore(builder.create());
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

    }

}
