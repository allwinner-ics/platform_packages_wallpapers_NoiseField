#pragma version(1)

#pragma rs java_package_name(com.android.noisefield)

#include "rs_graphics.rsh"
#pragma stateVertex(parent);
#pragma stateStore(parent);


rs_allocation textureDot;
rs_allocation textureBg;
rs_allocation textureVignette;

rs_program_vertex vertBg;
rs_program_fragment fragBg;

rs_program_vertex vertDots;
rs_program_fragment fragDots;

rs_program_store storeAlpha;
rs_program_store storeAdd;

typedef struct __attribute__((packed, aligned(4))) Particle {
    float3 position;
    float offsetX;
    float scaleSize;
    float speed;
    float wander;
    float alphaStart;
    float alpha;
    float life;
    float death;
} Particle_t;

typedef struct VpConsts {
    rs_matrix4x4 MVP;
} VpConsts_t;
VpConsts_t *vpConstants;

Particle_t *dotParticles; 
rs_mesh dotMesh;


float densityDPI;
float xOffset;





#define B 0x100
#define BM 0xff
#define N 0x1000

static int p[B + B + 2];
static float g3[B + B + 2][3];
static float g2[B + B + 2][2];
static float g1[B + B + 2];

static float noise_sCurve(float t)
{
    return t * t * (3.0f - 2.0f * t);
}

static void normalizef2(float v[])
{
    float s = (float)sqrt(v[0] * v[0] + v[1] * v[1]);
    v[0] = v[0] / s;
    v[1] = v[1] / s;
}

static void normalizef3(float v[])
{
    float s = (float)sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    v[0] = v[0] / s;
    v[1] = v[1] / s;
    v[2] = v[2] / s;
}

void init()
{
    int i, j, k;

    for (i = 0; i < B; i++) {
        p[i] = i;

        g1[i] = (float)(rsRand(B * 2) - B) / B;

        for (j = 0; j < 2; j++)
            g2[i][j] = (float)(rsRand(B * 2) - B) / B;
        normalizef2(g2[i]);

        for (j = 0; j < 3; j++)
            g3[i][j] = (float)(rsRand(B * 2) - B) / B;
        normalizef3(g3[i]);
    }

    for (i = B-1; i >= 0; i--) {
        k = p[i];
        p[i] = p[j = rsRand(B)];
        p[j] = k;
    }

    for (i = 0; i < B + 2; i++) {
        p[B + i] = p[i];
        g1[B + i] = g1[i];
        for (j = 0; j < 2; j++)
            g2[B + i][j] = g2[i][j];
        for (j = 0; j < 3; j++)
            g3[B + i][j] = g3[i][j];
    }
}

static float noisef2(float x, float y)
{
    int bx0, bx1, by0, by1, b00, b10, b01, b11;
    float rx0, rx1, ry0, ry1, sx, sy, a, b, t, u, v;
    float *q;
    int i, j;

    t = x + N;
    bx0 = ((int)t) & BM;
    bx1 = (bx0+1) & BM;
    rx0 = t - (int)t;
    rx1 = rx0 - 1.0f;

    t = y + N;
    by0 = ((int)t) & BM;
    by1 = (by0+1) & BM;
    ry0 = t - (int)t;
    ry1 = ry0 - 1.0f;

    i = p[bx0];
    j = p[bx1];

    b00 = p[i + by0];
    b10 = p[j + by0];
    b01 = p[i + by1];
    b11 = p[j + by1];

    sx = noise_sCurve(rx0);
    sy = noise_sCurve(ry0);

    q = g2[b00]; u = rx0 * q[0] + ry0 * q[1];
    q = g2[b10]; v = rx1 * q[0] + ry0 * q[1];
    a = mix(u, v, sx);

    q = g2[b01]; u = rx0 * q[0] + ry1 * q[1];
    q = g2[b11]; v = rx1 * q[0] + ry1 * q[1];
    b = mix(u, v, sx);

    //return 1.5f * mix(a, b, sy);
    return 8.0f * mix(a, b, sy);
}






void positionParticles(){
    rsDebug("HELLO!!!!!!!!!!", rsUptimeMillis());

    float width = rsgGetWidth();
    float height = rsgGetHeight();

    Particle_t* particle = dotParticles;
    int size = rsAllocationGetDimX(rsGetAllocation(dotParticles));
    
    
    
    for(int i=0; i<size; i++){
    

        particle->position.x = rsRand(-1.0f, 1.0f);
        particle->position.y = rsRand(-1.0f, 1.0f);
        particle->scaleSize = densityDPI/240.0f;
        particle->position.z = 0.0;
        particle->offsetX = xOffset;
        particle->speed = rsRand(0.0002f, 0.02f);
        particle->wander = rsRand(0.50f, 1.5f);
        particle->death = 0.0;
        particle->life = rsRand(300.0, 800.0f);
        particle->alphaStart = rsRand(0.01f, 1.0f);
        particle->alpha = particle->alphaStart;
        particle++;
        
        float dist = sqrt(particle->position.x*particle->position.x + particle->position.y*particle->position.y);
        if(dist < 0.75){
            dist = 0;
        } else {
            dist = dist-0.75;
        }
        if(particle->alpha < 1.0f){
            particle->alpha+=0.01;
            particle->alpha *= (1-dist);
        }
        
    }
    
    
    
    
}



int root(){
    float width = rsgGetWidth();
    float height = rsgGetHeight();
    
    
    
    rsgClearColor(0.0f, 0.f, 0.f, 0.5f);
    
    rsgBindProgramStore(storeAdd);
    
    // bg
    rsgBindProgramVertex(vertBg);
    rsgBindProgramFragment(fragBg);
    
    rsgBindTexture(fragBg, 0, textureBg);
    rsgDrawRect(0.0f, 0.0f, width, height, 0.0f);
    
    
    rsgBindProgramVertex(vertDots);
    rsgBindProgramFragment(fragDots);
    rsgBindTexture(fragDots, 0, textureDot);
    
    // dots
    Particle_t* particle = dotParticles;
    int size = rsAllocationGetDimX(rsGetAllocation(dotParticles));
    for(int i=0; i<size; i++){
        
        if(particle->life < 0 || particle->position.x < -1.1 || particle->position.x >1.1 || particle->position.y < -1.7 || particle->position.y >1.7){
            particle->position.x = rsRand(-1.0f, 1.0f);
            particle->position.y = rsRand(-1.0f, 1.0f);

            particle->speed = rsRand(0.0002f, 0.02f);
            particle->wander = rsRand(0.50f, 1.5f);
            particle->life = rsRand(300.0f, 800.0f);
            particle->alphaStart = rsRand(0.01f, 1.0f);
            particle->alpha = particle->alphaStart;
            
            particle->death = 0.0;
        }
        
        
        
        
            float noiseval = noisef2(particle->position.x*0.65, particle->position.y*0.65);
            
            float speed = noiseval * particle->speed + 0.01;
            float angle = 360 * noiseval * particle->wander;
            float rads = angle * 3.14159265 / 180.0;
        
            particle->position.x += cos(rads) * speed * 0.24;
            particle->position.y += sin(rads) * speed * 0.24;
        
    
            particle->life--;
            particle->death++;
        
            float dist = sqrt(particle->position.x*particle->position.x + particle->position.y*particle->position.y);
            if(dist < 0.75){
                dist = 0;
                particle->alphaStart *= (1-dist);
                
            } else {
                dist = dist-0.75;
                if(particle->alphaStart < 1.0f){
                    particle->alphaStart +=0.01;
                    particle->alphaStart *= (1-dist);
                    
                    
                }
            }
            
        
            if(particle->death < 101.0){
                particle->alpha = (particle->alphaStart)*(particle->death)/100.0;
            } else if(particle->life < 101.0){
                particle->alpha = particle->alpha*particle->life/100.0;
            } else {
                particle->alpha = particle->alphaStart;
            }
        
            particle->offsetX = xOffset;
        
        
        
        particle++;
    }
    
    
    rsgDrawMesh(dotMesh);

    return 35;
}
