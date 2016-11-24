#!/usr/bin/python
import os
navigator_images = ['favorite', 'files', 'settings', 'upload', 'manage_users', 'about']
action_images = ['add_dir', 'upload_file']
account_images = ['ic_account', 'ic_add', 'ic_launcher','ic_account_enterprise', 'add_user']
volumes_images = ['volume', 'volume_locked', 'volume_unlocked']
files_images = ['directory', 'file_code', 'file_txt', 'file_image', 'file_movie', 'file_music', 'file_pdf', 'file_presentation', 'file_calc', 'file_generic']
tasks = ['task_download_current', 'task_download', 'task_upload_current', 'task_upload', 'task_export', 'task_export_current']
logos = ['logo_account', 'menu_logo']
empty_bg = ['no_account_graphic', 'no_favorites_graphic', 'no_files_graphic', 'no_pending_graphic', 'no_volumes_graphic']
options = ['option_export', 'option_favourite', 'option_favourite_full', 'option_open_with', 'option_remove', 'option_rename', 'option_share', 'option_public_link']
stars = ['star', 'star_empty']

sizes = [ 	('mdpi', 	1), 
			('hdpi', 	1.5),
			('xhdpi', 	2),	
			('xxhdpi', 	3),	
			('xxxhdpi', 4) ]

res_dir = "app/src/main/res"

def convert(images, width):
	for size in sizes:
		os.system("mkdir -p "+res_dir+"/drawable-"+size[0])
		for img in images:
			os.system("rsvg-convert svg/"+img+".svg -a -w "+str((int)(size[1]*width))+" -o "+res_dir+"/drawable-"+size[0]+"/"+img+".png || echo error on file "+img)
	return
		
convert(navigator_images, 	24)	
convert(action_images,		24)
convert(account_images, 	48)
convert(volumes_images, 	48)		
convert(files_images,  		48)
convert(stars,		  		20)
convert(tasks,				48)
convert(logos,			   190)
convert(empty_bg,		   200)
convert(['add_button_plus'],14)
convert(['more'], 			24)
convert(['task_cancel'], 	24)
convert(['menu_bg'],	   280)
convert(['about_logo'],	   600)
convert(options,			20)
