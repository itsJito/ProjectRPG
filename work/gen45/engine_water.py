import os
E = r'C:\Users\John\Documents\ProjectRPG\theastrattoengine'
def patch(path, pairs):
    p = os.path.join(E, path)
    s = open(p, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:80])
        s = s.replace(old, new, 1)
    open(p, 'w', encoding='utf-8', newline='').write(s)

# ---- the C API: the rect command becomes a field
patch(r'native\include\astratto\astratto.h', [
 ('''    ASTRATTO_RCMD_WATER_FLOW = 21,    /* astratto_rcmd_water_flow: a stretch of water moving (x/z rect, m/s); this frame (API 2.10) */''',
  '''    ASTRATTO_RCMD_WATER_FIELD = 21,   /* astratto_rcmd_water_field + n*n RGBA8: the water's motion round the eye, kept until replaced (API 2.12; was 2.10's per-frame rects) */'''),
 ('''typedef struct astratto_rcmd_water_flow { astratto_record head; float x0,z0,x1,z1,vx,vz; } astratto_rcmd_water_flow;''',
  '''/* 2.12: a square of n x n cells from (x0, z0), cell metres each, row by row along x: R, G the flow along x and z
   (128 + 32 per m/s), B whitewater (0..255), A how much of the cell is open water (0..255) */
typedef struct astratto_rcmd_water_field { astratto_record head; float x0,z0,cell; uint32_t n,reserved; } astratto_rcmd_water_field;'''),
])

# ---- renderer state
patch(r'native\src\render\renderer.h', [
 ('''    std::vector<float> flows_;   // 2.10: x0, z0, x1, z1, vx, vz, -, - per moving stretch of water''',
  '''    /** 2.12: the water's motion round the eye (RGBA8, astratto_rcmd_water_field), its corner, cell and size (0 until one is sent). */
    gl::GLuint flowField_ = 0;
    float flowGrid_[4] = {0, 0, 1, 0};'''),
])

patch(r'native\src\render\renderer.cpp', [
 ('''    for(GLuint texture:{water_[0],water_[1],waterMask_})g_.ClearTexImage(texture,0,RGBA,FLOAT,nullptr);''',
  '''    for(GLuint texture:{water_[0],water_[1],waterMask_})g_.ClearTexImage(texture,0,RGBA,FLOAT,nullptr);
    // 2.12: the water's motion field (still water until the game sends one): 256 x 256 at most, drawn smooth
    flowField_=tex2D(RGBA8,256,256,LINEAR,1);
    { std::vector<uint8_t> still(256*256*4,0); for(size_t i=0;i<still.size();i+=4){still[i]=128;still[i+1]=128;} g_.BindTexture(TEXTURE_2D,flowField_);g_.TexSubImage2D(TEXTURE_2D,0,0,0,256,256,RGBA,UNSIGNED_BYTE,still.data()); }'''),
 ('''    smoke_.clear(); ripples_.clear(); flows_.clear();''', '''    smoke_.clear(); ripples_.clear();'''),
 ('''            case ASTRATTO_RCMD_WATER_FLOW: {
                astratto_rcmd_water_flow c;
                if(!readRecord(r,head.bytes,c)) { error_="short water flow record";return false; }
                if(!std::isfinite(c.x0+c.z0+c.x1+c.z1+c.vx+c.vz) || c.x1<c.x0 || c.z1<c.z0 || std::abs(c.vx)>8 || std::abs(c.vz)>8) { error_="invalid water flow";return false; }
                if(flows_.size()<32*8) { flows_.insert(flows_.end(),{c.x0,c.z0,c.x1,c.z1,c.vx,c.vz,0,0}); }
                break;
            }''',
  '''            case ASTRATTO_RCMD_WATER_FIELD: {
                astratto_rcmd_water_field c;
                if(!readRecord(r,head.bytes,c) || c.n==0 || c.n>256 || head.bytes<sizeof c+static_cast<uint64_t>(c.n)*c.n*4) { error_="invalid water field record";return false; }
                if(!std::isfinite(c.x0+c.z0+c.cell) || c.cell<=0 || c.cell>16) { error_="invalid water field";return false; }
                g_.BindTexture(TEXTURE_2D,flowField_);
                g_.PixelStorei(UNPACK_ALIGNMENT,1);
                g_.TexSubImage2D(TEXTURE_2D,0,0,0,static_cast<GLsizei>(c.n),static_cast<GLsizei>(c.n),RGBA,UNSIGNED_BYTE,r+sizeof c);
                g_.PixelStorei(UNPACK_ALIGNMENT,4);
                flowGrid_[0]=c.x0;flowGrid_[1]=c.z0;flowGrid_[2]=c.cell;flowGrid_[3]=static_cast<float>(c.n);
                break;
            }'''),
 ('''    // 2.10: where the water runs (rects and their velocity, two vec4s each): the step writes the flow beside the
    // surface's height, and the surface's texture drifts with it
    waterWaves_.i1("uFlows",static_cast<int>(flows_.size()/8));
    if(!flows_.empty())g_.Uniform4fv(waterWaves_.loc("uFlow"),static_cast<GLsizei>(flows_.size()/4),flows_.data());''',
  '''    // 2.12: where the water runs: the motion field the game sent (the step writes the flow beside the surface's height)
    waterWaves_.tex("uFlowField",1,TEXTURE_2D,flowField_);
    waterWaves_.f4("uFlowGrid",flowGrid_[0],flowGrid_[1],flowGrid_[2],flowGrid_[3]);'''),
 ('''    fWorld_.f3("uWaveGrid",waterOrigin_[0],waterOrigin_[1],WATER_CELL);''',
  '''    fWorld_.f3("uWaveGrid",waterOrigin_[0],waterOrigin_[1],WATER_CELL);
    fWorld_.tex("uFlowField",10,TEXTURE_2D,flowField_);
    fWorld_.f4("uFlowGrid",flowGrid_[0],flowGrid_[1],flowGrid_[2],flowGrid_[3]);'''),
])

# ---- Java bridge
patch(r'java\src\main\java\astratto\bridge\AstrattoNative.java', [
 ('RCMD_RIPPLE = 19, RCMD_WATER_FLOW = 21;', 'RCMD_RIPPLE = 19, RCMD_WATER_FIELD = 21;'),
])
patch(r'java\src\main\java\astratto\bridge\RenderCommands.java', [
 ('''    /** (2.10) A stretch of water running this frame: its rect in world x/z and its velocity (m/s). Up to 16 a frame. */
    public void waterFlow(float x0,float z0,float x1,float z1,float vx,float vz) { head(AstrattoNative.RCMD_WATER_FLOW,32); buf.putFloat(x0).putFloat(z0).putFloat(x1).putFloat(z1).putFloat(vx).putFloat(vz); }''',
  '''    /**
     * (2.12) The water's motion round the eye, kept until the next is sent: n x n cells from (x0, z0), cell metres each,
     * row by row along x, four bytes a cell - the flow along x and z (128 + 32 per m/s), whitewater, open water (0..255).
     */
    public void waterField(float x0,float z0,float cell,int n,java.nio.ByteBuffer cells) {
        int bytes=n*n*4;
        head(AstrattoNative.RCMD_WATER_FIELD,8+20+bytes); buf.putFloat(x0).putFloat(z0).putFloat(cell).putInt(n).putInt(0);
        for(int i=0;i<bytes;i++)buf.put(cells.get(i));
    }'''),
])
print('engine ok')
