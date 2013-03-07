using UnityEngine;
using System.Collections;
using System.IO;

public class ImagePickerSample : MonoBehaviour
{

    public Texture2D imageTexture;
	public GameObject sObject;
	
    // Use this for initialization
    void Start()
    {
        // 最初にコールバックの設定と初期化処理を実行.結果はコールバックに通知される.
        GetComponent<ImagePickerPlugin>().SetCallback(Callback);
        GetComponent<ImagePickerPlugin>().Init();
    }

    /**
     * Java層からの結果を受け取る.
     */
    public void Callback(string action, bool success)
    {
        Debug.Log("ImagePickerSample Callback called" + action);

        string imagePath = null;
        switch (action)
        {
        case "pick":
            if (success)
            {
                imagePath = GetComponent<ImagePickerPlugin>().GetImagePath();
            }
            break;
        case "capture":
            if (success)
            {
                imagePath = GetComponent<ImagePickerPlugin>().GetImagePath();
            }
            break;
        }

        if (imagePath != null)
        {
            imageTexture = new Texture2D(0, 0);
            imageTexture.LoadImage(LoadBytes(imagePath));
			if (sObject != null)
			{
				sObject.renderer.material.mainTexture = imageTexture;
			}
        }
    }

    private byte[] LoadBytes(string path)
    {
        FileStream fs = new FileStream(path, FileMode.Open);
        BinaryReader bin = new BinaryReader(fs);
        byte[] result = bin.ReadBytes((int)bin.BaseStream.Length);
        bin.Close();
        return result;
    }

    void OnGUI()
    {

        int width = Screen.width;
        int height = Screen.height;

        // 通常時.
        if (imageTexture != null)
        {
            //            GUI.Label(new Rect(width * 0.25f, height * 0f, width * 0.5f, height * 0.5f), imageTexture);
        }

        if (GUI.Button(new Rect(width * 0.25f, height * 0.5f, width * 0.25f, height * 0.25f), "PickImage"))
        {
            GetComponent<ImagePickerPlugin>().PickImage();
        }

        if (GUI.Button(new Rect(width * 0.5f, height * 0.5f, width * 0.25f, height * 0.25f), "CaptureImage"))
        {
            GetComponent<ImagePickerPlugin>().CaptureImage();
        }
    }
}
