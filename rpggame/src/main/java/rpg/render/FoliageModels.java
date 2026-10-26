package rpg.render;

import rpg.world.Foliage;

/** Eighteen reusable voxel silhouettes, combined into chunk meshes. Shapes use real thickness and several leaf colors. */
public final class FoliageModels {
    private FoliageModels() {}
    public static Meshes.Boxes model(Foliage.Kind kind,int seed) {
        Meshes.Boxes boxes=new Meshes.Boxes();int green=0x487A36,light=0x6D9849,dark=0x315A2D;
        switch(kind) {
            case FERN -> {
                for(int frond=0;frond<7;frond++) {
                    double angle=frond*Math.PI*2/7;
                    float px=0,py=0.015f,pz=0;
                    for(int step=1;step<=6;step++) {
                        float d=step*0.055f,y=0.02f+0.24f*(float)Math.sin(step*Math.PI/8),x=(float)Math.cos(angle)*d,z=(float)Math.sin(angle)*d;
                        boxes.rgb((x+px)/2,(y+py)/2,(z+pz)/2,Math.abs(x-px)+0.018f,Math.abs(y-py)+0.018f,Math.abs(z-pz)+0.018f,dark);
                        float width=(7-step)*0.014f;
                        for(int side:new int[]{-1,1})boxes.rgb(x-(float)Math.sin(angle)*width*side,y,z+(float)Math.cos(angle)*width*side,width*1.3f,0.022f,width,side>0?green:light);
                        px=x;py=y;pz=z;
                    }
                }
            }
            case TALL_GRASS -> {
                for(int blade=0;blade<9;blade++) {
                    float x=((seed>>>(blade%7))&7)*0.035f-0.12f,z=((seed>>>(blade%11+3))&7)*0.035f-0.12f,h=0.28f+blade%4*0.085f;
                    for(int step=0;step<4;step++)boxes.rgb(x+step*0.018f,h*(step+0.5f)/4,z,0.028f,h/4,0.025f,blade%2==0?light:green);
                }
            }
            case CLOVER -> {
                for(int stem=0;stem<6;stem++) {
                    double angle=stem*2.4;float x=(float)Math.cos(angle)*0.14f,z=(float)Math.sin(angle)*0.14f,y=0.045f+stem%3*0.025f;
                    boxes.rgb(x,y/2,z,0.018f,y,0.018f,dark);
                    for(int leaf=0;leaf<3;leaf++){double a=leaf*Math.PI*2/3;boxes.rgb(x+(float)Math.cos(a)*0.035f,y,z+(float)Math.sin(a)*0.035f,0.07f,0.02f,0.06f,leaf==0?light:green);}
                }
            }
            case REED -> {
                for(int stem=0;stem<5;stem++){float x=stem*0.07f-0.14f,z=(stem%2)*0.1f-0.05f,h=0.65f+stem%3*0.13f;
                    boxes.rgb(x,h/2,z,0.025f,h,0.025f,0x78864A);boxes.rgb(x,h-0.03f,z,0.055f,0.16f,0.055f,0x6A4A32);
                    boxes.rgb(x+0.05f,h*0.35f,z,0.12f,0.025f,0.035f,green);}
            }
            case BRAMBLE -> {
                for(int stem=0;stem<5;stem++){double angle=stem*2.4;float x=(float)Math.cos(angle)*0.2f,z=(float)Math.sin(angle)*0.2f;
                    boxes.rgb(x,0.21f,z,0.025f,0.42f,0.025f,0x705336);
                    for(int leaf=0;leaf<4;leaf++)boxes.rgb(x+(leaf%2==0?0.06f:-0.06f),0.13f+leaf*0.085f,z,0.12f,0.035f,0.09f,leaf%2==0?dark:green);
                    if((stem&1)==0)boxes.rgb(x,0.36f,z-0.065f,0.035f,0.035f,0.035f,0x44283B);}
            }
            case WILDFLOWER -> {
                for(int stem=0;stem<4;stem++){float x=stem*0.075f-0.11f,z=(stem%2)*0.12f-0.06f,h=0.21f+stem%3*0.08f;
                    boxes.rgb(x,h/2,z,0.018f,h,0.018f,green);int petal=(seed&1)==0?0xE9DAA0:0xB991B9;
                    boxes.rgb(x,h,z,0.11f,0.025f,0.045f,petal);boxes.rgb(x,h,z,0.045f,0.025f,0.11f,petal);boxes.rgb(x,h+0.015f,z,0.03f,0.018f,0.03f,0xD1A644);}
            }
            case IVY -> {
                for(int strand=0;strand<3;strand++)for(int step=0;step<8;step++) {
                    float x=(strand-1)*0.15f+(float)Math.sin(step*1.7)*0.035f,y=step*0.17f;
                    boxes.rgb(x,y+0.08f,0.36f,0.018f,0.18f,0.018f,0x58653B);
                    boxes.rgb(x+(step%2==0?0.045f:-0.045f),y+0.08f,0.34f,0.12f,0.09f,0.035f,step%3==0?light:green);
                }
            }
            case MOSS -> {
                for(int row=0;row<5;row++)for(int col=0;col<6;col++)if(((row*7+col+seed)&3)!=0)
                    boxes.rgb((col-2.5f)*0.065f,row*0.055f+0.025f,0.43f,0.075f,0.065f,0.03f,(row+col)%2==0?0x5B7040:0x75834B);
            }
            case MUSHROOM -> {
                for(int stem=0;stem<4;stem++){float x=stem*0.08f-0.12f,z=(stem%2)*0.13f-0.06f,h=0.06f+stem%3*0.035f;
                    boxes.rgb(x,h/2,z,0.025f,h,0.025f,0xC7B99B);boxes.rgb(x,h,z,0.11f,0.035f,0.1f,0x9A6A45);boxes.rgb(x,h+0.025f,z,0.07f,0.025f,0.065f,0xB08B63);}
            }
            case SHRUB -> {
                // a low rounded bush: a woody base, then clumps of leaf in three greens stacked into a dome
                for(int stem=0;stem<3;stem++){double a=stem*2.1;boxes.rgb((float)Math.cos(a)*0.05f,0.08f,(float)Math.sin(a)*0.05f,0.03f,0.16f,0.03f,0x5E4A30);}
                for(int clump=0;clump<11;clump++){
                    double a=clump*2.39+seed%5;float r=clump<6?0.2f:0.1f,y=clump<6?0.2f:0.36f+(clump%3)*0.03f;
                    boxes.rgb((float)Math.cos(a)*r,y,(float)Math.sin(a)*r,0.17f,0.13f,0.16f,clump%3==0?light:clump%3==1?green:dark);
                }
            }
            case THISTLE -> {
                for(int stem=0;stem<3;stem++){float x=stem*0.09f-0.09f,z=(stem%2)*0.08f-0.04f,h=0.38f+stem*0.07f;
                    boxes.rgb(x,h/2,z,0.022f,h,0.022f,0x5C7A44);
                    for(int leaf=0;leaf<3;leaf++)boxes.rgb(x+(leaf%2==0?0.05f:-0.05f),0.08f+leaf*0.09f,z,0.1f,0.02f,0.04f,0x6E8C58);
                    boxes.rgb(x,h,z,0.055f,0.05f,0.055f,0x56703E);boxes.rgb(x,h+0.045f,z,0.065f,0.045f,0.065f,0x9A5CB4);}
            }
            case DAISY -> {
                for(int stem=0;stem<6;stem++){double a=stem*2.4;float x=(float)Math.cos(a)*0.12f,z=(float)Math.sin(a)*0.12f,h=0.1f+stem%3*0.04f;
                    boxes.rgb(x,h/2,z,0.015f,h,0.015f,green);
                    boxes.rgb(x,h,z,0.07f,0.016f,0.07f,0xF2EFE6);boxes.rgb(x,h+0.01f,z,0.026f,0.016f,0.026f,0xE8B83A);}
                for(int leaf=0;leaf<5;leaf++){double a=leaf*1.25;boxes.rgb((float)Math.cos(a)*0.06f,0.012f,(float)Math.sin(a)*0.06f,0.08f,0.015f,0.04f,light);}
            }
            case BLUEBELL -> {
                for(int stem=0;stem<5;stem++){float x=stem*0.07f-0.14f,z=(stem%2)*0.1f-0.05f,h=0.2f+stem%3*0.05f;
                    boxes.rgb(x,h/2,z,0.016f,h,0.016f,green);
                    for(int bell=0;bell<3;bell++)boxes.rgb(x+0.03f,h-bell*0.045f,z,0.035f,0.04f,0.035f,bell==0?0x6A78D8:0x5462C0);
                    boxes.rgb(x-0.04f,0.05f,z,0.03f,0.1f,0.012f,dark);}
            }
            case CATTAIL -> {
                for(int stem=0;stem<4;stem++){float x=stem*0.08f-0.12f,z=(stem%2)*0.09f-0.045f,h=0.8f+stem%2*0.18f;
                    boxes.rgb(x,h/2,z,0.02f,h,0.02f,0x7A8A4C);boxes.rgb(x,h-0.08f,z,0.05f,0.14f,0.05f,0x5A3A22);boxes.rgb(x,h+0.02f,z,0.01f,0.06f,0.01f,0x7A8A4C);
                    boxes.rgb(x+0.04f,h*0.45f,z,0.03f,h*0.7f,0.012f,green);}
            }
            case HEATHER -> {
                for(int tuft=0;tuft<9;tuft++){double a=tuft*2.4;float r=0.04f+tuft*0.018f,x=(float)Math.cos(a)*r,z=(float)Math.sin(a)*r,h=0.12f+tuft%3*0.03f;
                    boxes.rgb(x,h/2,z,0.04f,h,0.04f,0x55623A);boxes.rgb(x,h,z,0.05f,0.04f,0.05f,tuft%2==0?0xB06AA8:0x9A5A9A);}
            }
            case DOCK -> {
                // broad low leaves fanned from the root, a rusty seed spike in the middle
                for(int leaf=0;leaf<6;leaf++){double a=leaf*Math.PI/3+seed%3;float x=(float)Math.cos(a)*0.13f,z=(float)Math.sin(a)*0.13f;
                    boxes.rgb(x,0.04f+leaf%2*0.03f,z,Math.abs((float)Math.cos(a))*0.16f+0.07f,0.02f,Math.abs((float)Math.sin(a))*0.16f+0.07f,leaf%2==0?green:dark);}
                boxes.rgb(0,0.2f,0,0.02f,0.36f,0.02f,0x7A5A34);boxes.rgb(0,0.3f,0,0.05f,0.14f,0.05f,0x8C4A2A);
            }
            case BRACKEN -> {
                for(int frond=0;frond<5;frond++){double a=frond*Math.PI*2/5+0.3;float px=0,py=0,pz=0;
                    for(int step=1;step<=7;step++){float d=step*0.065f,y=0.03f+0.38f*(float)Math.sin(step*Math.PI/10),x=(float)Math.cos(a)*d,z=(float)Math.sin(a)*d;
                        boxes.rgb((x+px)/2,(y+py)/2,(z+pz)/2,Math.abs(x-px)+0.016f,Math.abs(y-py)+0.016f,Math.abs(z-pz)+0.016f,0x6A6A34);
                        float w=(8-step)*0.012f;boxes.rgb(x,y,z,w*2+0.03f,0.02f,w*2+0.03f,step%2==0?0x7A8A3C:0x8C9444);px=x;py=y;pz=z;}}
            }
            case SAPLING -> {
                boxes.rgb(0,0.25f,0,0.035f,0.5f,0.035f,0x78603E);
                for(int branch=0;branch<5;branch++){double a=branch*2.4;float x=(float)Math.cos(a)*0.09f,z=(float)Math.sin(a)*0.09f,y=0.2f+branch*0.075f;
                    boxes.rgb(x/2,y,z/2,Math.abs(x)+0.02f,0.02f,Math.abs(z)+0.02f,0x78603E);boxes.rgb(x,y+0.035f,z,0.17f,0.08f,0.14f,branch%2==0?light:green);}
            }
        }
        return boxes;
    }
    static float[] chunk(Foliage.Chunk chunk) {
        Meshes.Boxes merged=new Meshes.Boxes();
        for(var plant:chunk.plants)if(!plant.removed) {
            float co=(float)Math.cos(plant.facing),si=(float)Math.sin(plant.facing);
            // the plant's root, packed into the vertex alpha for the bend (model.vert): every part leans from it
            float root=2+Math.clamp(Math.round((plant.x-chunk.x)*16),0,255)+256*Math.clamp(Math.round((plant.z-chunk.z)*16),0,255);
            for(float[] box:model(plant.kind,plant.seed).list) {
                float x=box[0],z=box[2];float wx=plant.x-chunk.x+co*x+si*z,wz=plant.z-chunk.z-si*x+co*z;
                // Keep the tiny voxels axis aligned when rotating a bank model; thickness remains real geometry.
                float sx=Math.abs(co)*box[3]+Math.abs(si)*box[5],sz=Math.abs(si)*box[3]+Math.abs(co)*box[5];
                merged.list.add(new float[]{wx,box[1],wz,sx,box[4],sz,box[6],box[7],box[8],root});
            }
        }
        return merged.vertices();
    }
}
